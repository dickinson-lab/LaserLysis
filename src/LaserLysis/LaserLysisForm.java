/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package LaserLysis;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JFormattedTextField;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import mmcorej.TaggedImage;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Coordinates;
import org.micromanager.Studio;
import org.micromanager.data.Coords; 
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplayWindow;
import org.micromanager.PropertyMap;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import javax.swing.BoxLayout;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import org.micromanager.PropertyMaps;
import org.micromanager.data.Metadata;
import org.micromanager.data.Pipeline;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;

/**
 *
 * @author Dickinson Lab
 */
public class LaserLysisForm extends JFrame { 
    private final Studio gui_;
    private final mmcorej.CMMCore mmc_;    
    
    String[] none = {"<none>"};
    ArrayList<String> shotTimes = new ArrayList<>();
    double startPosition;

    // Initialize Dialog Box Components
    private final JPanel AcquisitionPanel = new JPanel();
    // Channel Information
    private final JLabel label_ch1 = new JLabel("<html>Channel:</html");
    private final JComboBox channel1 = new JComboBox(none);
    private final JLabel label_n_ch1= new JLabel("<html>Frames:</html>");
    private final JFormattedTextField textField_n_ch1= new JFormattedTextField();
    private final JLabel label_exp_ch1= new JLabel("<html>Exposure:</html>");
    private final JFormattedTextField textField_exp_ch1= new JFormattedTextField();

    // Save location
    private final JLabel label_dir = new JLabel("<html>Save Location:</html>");
    private final JFormattedTextField textField_dir = new JFormattedTextField();
    private final JButton dirButton = new JButton("...");
    private final JFileChooser dirChooser = new JFileChooser();
    private final JLabel label_name = new JLabel("<html>Name Prefix:</html>");
    private final JFormattedTextField textField_name = new JFormattedTextField();    
    
    // Start button
    private final JButton startButton = new JButton("Start Acquisition");
    
    // Initialize controls for pulsed IR laser
    private final JPanel LaserPanel = new JPanel();
    private final ImageIcon laserIcon = new ImageIcon("C:/Program Files/Micro-Manager-2.0gamma/scripts/laser-icon-small.jpg");
    private final JButton fireButton = new JButton("Fire!", laserIcon);
    private final JButton resetButton = new JButton("Clear shot log");
    private final JLabel label_alignment = new JLabel("Pulsed laser alignment tools. Experts only!");
    private final JSwitchBox alignmentSwitch = new JSwitchBox("enabled","disabled");
    private final JLabel label_blinkSwitch = new JLabel("Enable firing every 0.5s");
    private final JSwitchBox blinkSwitch = new JSwitchBox("on","off");
    private final JLabel label_contSwitch = new JLabel("Enable continuous firing at 490 Hz");
    private final JSwitchBox contSwitch = new JSwitchBox("on","off");

    private String startPos; //Arduino shutter start position
    
    private acquisitionWorker acquisition;
    
    public LaserLysisForm(Studio gui) throws Exception {
        gui_ = gui;
        mmc_ = gui_.core();
        makeDialog();
    }
    
    class acquisitionWorker extends SwingWorker<Void, Void> {
        @Override
        protected Void doInBackground() throws Exception {
            //Get info from dialog
            String chGroup = mmc_.getChannelGroup();
            String ch1 = (String)channel1.getSelectedItem();
            String ch1_fileSuffix = ch1.replaceAll("\\+","-");
            int n_ch1 = Integer.parseInt(textField_n_ch1.getText().replaceAll(",", ""));
            int exp_ch1 = Integer.parseInt(textField_exp_ch1.getText().replaceAll(",", ""));
            
            String dir = textField_dir.getText();
            dir = dir.replaceAll("\\\\","/");
            String baseName = textField_name.getText();
            //Determine Name to Save
            String posName = baseName;
            int b=1;
            posName = baseName + "_" + String.format("%02d",new Object[]{b});
            File im_ch1 = new File(dir+"/"+posName);
            while ( im_ch1.exists() ) {
                    b++;
                    posName = baseName + "_" + String.format("%02d",new Object[]{b});
                    im_ch1 = new File(dir+"/"+posName);
            }
            
            // Check for saving directory, create if necessary
            File folder = new File(dir);
            if (!folder.exists()) {
                    folder.mkdir();
            }
            String savePath = dir+"/"+posName;
            
            //Acquire Images
            if ( n_ch1>0 && !ch1.equals(none)) {
                acquireChannel(chGroup, ch1, n_ch1, exp_ch1, savePath);
            }
            
            return null;
        }
    }        
    
    // Method to acquire a single channel of imaging data
    private void acquireChannel(String chGroup, String ch, int n, int exp, String savePath) {
        try {
            // Select Configuration
            mmc_.setConfig(chGroup, ch);
            
            // Initialize Acquisition
            Datastore store = gui_.data().createNDTIFFDatastore(savePath);

            //Update display settings
            DisplaySettings.Builder settingsBuilder = gui_.displays().displaySettingsFromProfile(
                                                            PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key()).copyBuilder();            
            if (settingsBuilder == null) {
                settingsBuilder = DefaultDisplaySettings.builder();
            }

            DisplayWindow disp = gui_.displays().createDisplay(store, null,settingsBuilder.build()); //In the future, could replace "null" with controls if I can figure out how that's supposed to work
            disp.setDisplaySettingsProfileKey(PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
            
            // Perform Acquisition
            mmc_.setExposure(exp);
            // Arguments are the number of images to collect, the amount of time to wait
            // between images, and whether or not to halt the acquisition if the
            // sequence buffer overflows.
            mmc_.startSequenceAcquisition(n, 0, false);
            
            // Set up a Coords.CoordsBuilder for applying coordinates to each image.
            //Coords.CoordsBuilder builder = gui_.data().getCoordsBuilder();
            Coords.Builder builder = Coordinates.builder();
            
            // set up SummaryMetadataBuilder
            SummaryMetadata.Builder meta = gui_.data().getSummaryMetadataBuilder(); // <<<< initialize meta builder

            // Save Summary metadata
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX").format(new Date());
            String XYStage = mmc_.getXYStageDevice();
            double currentPosition = mmc_.getYPosition(XYStage);
            double distanceMoved = currentPosition - startPosition;
            PropertyMap myParams = PropertyMaps.builder().putStringList("Shot Times",shotTimes).
                                                          putDouble("Y Distance Moved",distanceMoved).
                                                          build();
            store.setSummaryMetadata(meta.startDate(timeStamp).
                                          intendedDimensions(builder.c(1).z(1).t(n).p(1).build()).
                                          axisOrder(Coords.CHANNEL,Coords.Z_SLICE,Coords.TIME_POINT, Coords.STAGE_POSITION).        
                                          userData(myParams).
                                          build() );

            // Set up MetadataBuilder for individual image planes
            Metadata.Builder mb = gui_.data().metadataBuilder();
            
            // Set up image processors
            Pipeline pipeLine = gui_.data().copyApplicationPipeline(store, true);
            
            
            int width = (int) mmc_.getImageWidth();
            int height = (int) mmc_.getImageHeight();
            int frame = 0;
            while ( mmc_.getRemainingImageCount() > 0 || mmc_.isSequenceRunning(mmc_.getCameraDevice()) ) {
                if (mmc_.getRemainingImageCount() > 0) {
                    TaggedImage tagged = mmc_.popNextTaggedImage(); 
                    Image image = gui_.data().convertTaggedImage(tagged,
                                                           builder.c(0).t(frame).p(0).z(0).build(), 
                                                           null);
                    if (frame != 0) {
                        //Erase redundant metadata for all but the first frame
                        image = image.copyWithMetadata(mb.bitDepth(image.getMetadata().getBitDepth()).
                                                          elapsedTimeMs(image.getMetadata().getElapsedTimeMs(0)).
                                                          imageNumber(image.getMetadata().getImageNumber()).
                                                          build() );
                    }
                    
                    pipeLine.insertImage(image); //Pass image to the processor pipeline instead of putting it directly into the dataStore
                    //store.putImage(image);
                    frame++;
                } else {
                    mmc_.sleep(Math.min(0.5 * exp, 20));
                }
            }
            mmc_.stopSequenceAcquisition();
            //ij.IJ.setSlice(1);

            // Save and close
            
            /* //Save with ImageJ
            ToImageJ dump = new ToImageJ();
            ImagePlus iPlus = dump.toImageJ(store, display, false);
            FileSaver saver = new FileSaver(iPlus);
            saver.saveAsTiffStack(savePath+".tif");
            iPlus.close();
            */
            
            // Save with MM
            store.freeze();
            gui_.displays().closeDisplaysFor(store);
            store.close();
            shotTimes.clear(); //Erase laser shot log so we have a fresh start for the next acquisition
            System.gc(); //Clean up
        } catch (Exception ex) {
            Logger.getLogger(LaserLysisForm.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void makeDialog() throws Exception {
        //Get Stage Info
        String XYStage = mmc_.getXYStageDevice();
        startPosition = mmc_.getYPosition(XYStage);
    
        // Set Default Values
        textField_n_ch1.setValue(0);
        textField_n_ch1.setColumns(4);
        textField_n_ch1.setEditable(true);
        textField_exp_ch1.setValue(50);
        textField_exp_ch1.setColumns(4);
        textField_exp_ch1.setEditable(true);
        textField_dir.setColumns(20);
        textField_dir.setEditable(true);
        dirChooser.setApproveButtonText("Select Directory");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        textField_name.setColumns(20);
        textField_name.setEditable(true);
        alignmentSwitch.setSelected(false);
        alignmentSwitch.setEnabled(true);
        blinkSwitch.setSelected(false);
        blinkSwitch.setEnabled(false);
        contSwitch.setSelected(false);
        contSwitch.setEnabled(false);
        
        // Populate drop-down menus
        String chanGroup =mmc_.getChannelGroup();
        mmcorej.StrVector channelVector = mmc_.getAvailableConfigs(chanGroup);
        String[] channelList = channelVector.toArray();
        for (String channel:channelList) {
            channel1.addItem(channel);
        }
        // Add components to acquisition panel 
        TitledBorder title1 = BorderFactory.createTitledBorder("Acquisition Controls");
        Border empty = BorderFactory.createEmptyBorder(10,10,10,10);
        AcquisitionPanel.setBorder(BorderFactory.createCompoundBorder(empty,title1));
        AcquisitionPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 0;
        AcquisitionPanel.add(label_ch1,c);
        c.gridx = 1;
        AcquisitionPanel.add(channel1,c);
        c.gridx = 2;
        AcquisitionPanel.add(label_n_ch1,c);
        c.gridx = 3;
        AcquisitionPanel.add(textField_n_ch1,c);
        c.gridx = 4;
        AcquisitionPanel.add(label_exp_ch1,c);
        c.gridx = 5;
        AcquisitionPanel.add(textField_exp_ch1,c);
        c.gridx = 0;
        c.gridy = 1;
        AcquisitionPanel.add(label_dir,c);
        c.gridx = 1;
        c.gridwidth = 2;
        AcquisitionPanel.add(textField_dir,c);
        c.gridx = 3;
        c.gridwidth = 1;
        AcquisitionPanel.add(dirButton,c);
        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 1;
        AcquisitionPanel.add(label_name,c);
        c.gridx = 1;
        c.gridwidth = 2;
        AcquisitionPanel.add(textField_name,c);
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 4;
        c.anchor = GridBagConstraints.CENTER;
        AcquisitionPanel.add(startButton,c);
        
        // Add components to laser panel
        TitledBorder title2 = BorderFactory.createTitledBorder("Pulsed IR Lysis Laser Conrols");
        LaserPanel.setBorder(BorderFactory.createCompoundBorder(empty,title2));
        LaserPanel.setLayout(new GridBagLayout());
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(10,10,10,10);
        d.anchor = GridBagConstraints.CENTER;
        d.gridwidth = 2;
        d.gridx = 0;
        d.gridy = 0;
        LaserPanel.add(fireButton,d);
        d.gridy = 1;
        LaserPanel.add(resetButton,d);
        d.gridy = 2;
        d.anchor = GridBagConstraints.LINE_START;
        LaserPanel.add(label_alignment,d);
        d.gridy = 3;
        d.anchor = GridBagConstraints.CENTER;
        LaserPanel.add(alignmentSwitch,d);
        d.gridy = 4;
        d.anchor = GridBagConstraints.LINE_START;
        d.gridwidth = 1;
        LaserPanel.add(label_blinkSwitch,d);
        d.gridx = 1; 
        LaserPanel.add(label_contSwitch,d);
        d.gridx = 0;
        d.gridy = 5;
        d.anchor = GridBagConstraints.CENTER;
        LaserPanel.add(blinkSwitch,d);
        d.gridx = 1;
        LaserPanel.add(contSwitch,d);
        
        // Arrange GUI window
        setTitle("Dynamic SiMPull Acquisition");
        setSize(550, 600);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle rect = defaultScreen.getDefaultConfiguration().getBounds();
        int x = (int) rect.getMaxX() - 550;
        setLocation(x, 500);
        
        Container cp = getContentPane();
        cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));
        cp.add(AcquisitionPanel);
        cp.add(LaserPanel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);        
        
        // Directory Chooser button
        dirButton.addActionListener(new ActionListener() { 
            @Override
            public void actionPerformed(ActionEvent eText) {
                if (dirChooser.showOpenDialog(LaserLysisForm.this) == JFileChooser.APPROVE_OPTION) {
                    File dir = dirChooser.getSelectedFile();
                    textField_dir.setValue(dir.getPath());
                }
            }
        });     

        // Acquire dynamic SiMPull data on button press
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent eText) {
                // Start acquisition
                acquisition = new acquisitionWorker();
                acquisition.execute();
            }
        });

        //Fire IR laser on Button press
        fireButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent eText) {
        	try {
                    // Get initial Arduino shutter state
                    startPos = mmc_.getProperty("Arduino-Switch","State");

                    // Fire the laser
                    mmc_.setProperty("Arduino-Switch", "State", 1);
                    mmc_.setProperty("Arduino-Shutter", "OnOff", 1); 
                    mmc_.sleep(10);
                    mmc_.setProperty("Arduino-Shutter", "OnOff", 0);
                    
                    // Return to initial shutter state
                    mmc_.setProperty("Arduino-Switch", "State", startPos);
                    
                    // Add log information
                    startPosition = mmc_.getYPosition(XYStage);
                    String shotTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX").format(new Date());
                    shotTimes.add(shotTime);
                }
                catch (Exception e) {
                    //TODO error handling
                }
            }
        });
       
        //Clear laser shot log on button press
        resetButton.addActionListener(new ActionListener() {
           @Override
           public void actionPerformed(ActionEvent eText) {
               shotTimes.clear();
           }
        });
        
        //Alignment switch controls
        alignmentSwitch.addMouseListener( new MouseAdapter() {
            @Override
            public void mouseReleased( MouseEvent e ) {
                if(new Rectangle( getPreferredSize() ).contains( e.getPoint() )) {
                    blinkSwitch.setEnabled( alignmentSwitch.isSelected() );
                    contSwitch.setEnabled( alignmentSwitch.isSelected() );
                }
            }
        });
        
        blinkSwitch.addMouseListener( new MouseAdapter() {
            @Override
            public void mouseReleased( MouseEvent e ) {
                if(new Rectangle( getPreferredSize() ).contains( e.getPoint() )) {
                    try {
                        if ( blinkSwitch.isSelected() ) {
                            startPos = mmc_.getProperty("Arduino-Switch","State");
                            mmc_.setProperty("Arduino-Switch", "State", 2);
                            mmc_.setProperty("Arduino-Shutter", "OnOff", 1);
                        } else {
                            mmc_.setProperty("Arduino-Shutter", "OnOff", 0);
                            mmc_.setProperty("Arduino-Switch", "State", startPos);
                        }
                    }
                    catch (Exception error) {
                        //TODO error handling
                    }
                }
            }
        });
         
        contSwitch.addMouseListener( new MouseAdapter() {
            @Override
            public void mouseReleased( MouseEvent e ) {
                if(new Rectangle( getPreferredSize() ).contains( e.getPoint() )) {
                    try {
                        if ( contSwitch.isSelected() ) {
                            startPos = mmc_.getProperty("Arduino-Switch","State");
                            mmc_.setProperty("Arduino-Switch", "State", 8);
                            mmc_.setProperty("Arduino-Shutter", "OnOff", 1);
                        } else {
                            mmc_.setProperty("Arduino-Shutter", "OnOff", 0);
                            mmc_.setProperty("Arduino-Switch", "State", startPos);
                        }
                    }
                    catch (Exception error) {
                        //TODO error handling
                    }
                }
            }
        });
        
    }

}
