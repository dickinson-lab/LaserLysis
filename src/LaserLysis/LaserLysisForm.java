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
import static java.lang.Math.max;
import java.lang.Thread;
import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.control.Labeled; 
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JFormattedTextField;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
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

import ij.ImagePlus;
import ij.io.FileSaver;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.List;
import java.awt.Rectangle;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Set;
import org.micromanager.PropertyMaps;

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
    // Channel Information
    private final JLabel label_ch1 = new JLabel("<html>Channel & Exposure information:</html");
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
            String ch1 = (String)channel1.getSelectedItem();
            String ch1_fileSuffix = ch1.replaceAll("\\+","-");
            int n_ch1 = Integer.parseInt(textField_n_ch1.getText());
            int exp_ch1 = Integer.parseInt(textField_exp_ch1.getText());
            
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
                                    
            //Acquire Images
            if ( n_ch1>0 && !ch1.equals(none)) {
                acquireChannel(ch1, n_ch1, exp_ch1, posName, dir);
            }
            
            return null;
        }
    }        
    
    // Method to acquire a single channel of imaging data
    private void acquireChannel(String ch, int n, int exp, String acqName, String dir) {
        try {
            // Select Configuration
            mmc_.setConfig(" ImagingChannel", ch);
            
            // Initialize Acquisition
            Datastore store = gui_.data().createRAMDatastore();
            DisplayWindow display = gui_.displays().createDisplay(store);
            mmc_.setExposure(exp);
            
            // Perform Acquisition
            // Arguments are the number of images to collect, the amount of time to wait
            // between images, and whether or not to halt the acquisition if the
            // sequence buffer overflows.
            mmc_.startSequenceAcquisition(n, 0, false);
            
            // Set up a Coords.CoordsBuilder for applying coordinates to each image.
            //Coords.CoordsBuilder builder = gui_.data().getCoordsBuilder();
            Coords.Builder builder = Coordinates.builder();
            
            // set up SummaryMetadataBuilder
            SummaryMetadata.Builder meta = gui_.data().getSummaryMetadataBuilder(); // <<<< initialize meta builder

            // Save metadata
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS XXX").format(new Date());
            String XYStage = mmc_.getXYStageDevice();
            double currentPosition = mmc_.getYPosition(XYStage);
            double distanceMoved = currentPosition - startPosition;
            PropertyMap myParams = PropertyMaps.builder().putStringList("Shot Times",shotTimes).
                                                          putDouble("Y Distance Moved",distanceMoved).
                                                          build();
            store.setSummaryMetadata(meta.startDate(timeStamp).
                                          userData(myParams).
                                          build() );

          
            
            int frame = 0;
            while ( mmc_.getRemainingImageCount() > 0 || mmc_.isSequenceRunning(mmc_.getCameraDevice()) ) {
                if (mmc_.getRemainingImageCount() > 0) {
                    TaggedImage tagged = mmc_.popNextTaggedImage();
                    Image image = gui_.data().convertTaggedImage(tagged,
                                                                 builder.c(0).t(frame).p(0).z(0).build(), 
                                                                 null);
                    store.putImage(image);
                    frame++;
                } else {
                    mmc_.sleep(Math.min(0.5 * exp, 20));
                }
            }
            mmc_.stopSequenceAcquisition();
            //ij.IJ.setSlice(1);

            // Save and close
            String savePath = dir+"/"+acqName;
            
            /* //Save with ImageJ
            ToImageJ dump = new ToImageJ();
            ImagePlus iPlus = dump.toImageJ(store, display, false);
            FileSaver saver = new FileSaver(iPlus);
            saver.saveAsTiffStack(savePath+".tif");
            iPlus.close();
            */
            
            // Save with MM
            store.save(Datastore.SaveMode.MULTIPAGE_TIFF, savePath);
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
        
        // Populate drop-down menus
        mmcorej.StrVector channelGroupVector = mmc_.getAvailableConfigGroups();
        String[] groupList =channelGroupVector.toArray();
        mmcorej.StrVector channelVector = mmc_.getAvailableConfigs(groupList[0]);
        String[] channelList = channelVector.toArray();
        for (String channel:channelList) {
            channel1.addItem(channel);
        }
        
        // Add components to laser panel
        LaserPanel.setBorder(BorderFactory.createTitledBorder("Pulsed IR Lysis Laser Conrols"));
        LaserPanel.setLayout(new GridBagLayout());
        GridBagConstraints d = new GridBagConstraints();
        d.insets = new Insets(10,10,10,10);
        d.anchor = GridBagConstraints.CENTER;
        d.gridwidth = 1;
        d.gridx = 0;
        d.gridy = 0;
        LaserPanel.add(fireButton,d);
        d.gridy = 1;
        LaserPanel.add(resetButton,d);
        
        // Arrange GUI window
        setTitle("Dynamic SiMPull Acquisition");
        setSize(550, 450);
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice defaultScreen = ge.getDefaultScreenDevice();
        Rectangle rect = defaultScreen.getDefaultConfiguration().getBounds();
        int x = (int) rect.getMaxX() - 550;
        setLocation(x, 500);
        
        Container cp = getContentPane();
        cp.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 0;
        cp.add(label_ch1,c);
        c.gridy = 1;
        cp.add(channel1,c);
        c.gridx = 1;
        cp.add(label_n_ch1,c);
        c.gridx = 2;
        cp.add(textField_n_ch1,c);
        c.gridx = 3;
        cp.add(label_exp_ch1,c);
        c.gridx = 4;
        cp.add(textField_exp_ch1,c);
        c.gridx = 0;
        c.gridy = 2;
        cp.add(label_dir,c);
        c.gridx = 1;
        c.gridwidth = 2;
        cp.add(textField_dir,c);
        c.gridx = 3;
        c.gridwidth = 1;
        cp.add(dirButton,c);
        c.gridy = 3;
        c.gridx = 0;
        c.gridwidth = 1;
        cp.add(label_name,c);
        c.gridx = 1;
        c.gridwidth = 2;
        cp.add(textField_name,c);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 4;
        c.anchor = GridBagConstraints.CENTER;
        cp.add(startButton,c);
        c.gridy = 5;
        c.gridheight = 2;
        c.ipadx = 100;
        cp.add(LaserPanel,c);
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
                int n_ch1 = Integer.parseInt(textField_n_ch1.getText());
                int exp_ch1 = Integer.parseInt(textField_exp_ch1.getText());
                
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
                    // Fire the laser
                    mmc_.setProperty("Arduino-Switch", "State", 1);
                    mmc_.setProperty("561 Shutter", "OnOff", 1); //Even though this is called 561 shutter, it's just the Arduino.
                    mmc_.sleep(10);
                    mmc_.setProperty("561 Shutter", "OnOff", 0);
                    mmc_.setProperty("Arduino-Switch", "State", 16);
                    
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
        
    }

}
