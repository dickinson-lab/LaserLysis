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
import javax.swing.SwingWorker;
import mmcorej.TaggedImage;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Coordinates;
import org.micromanager.Studio;
import org.micromanager.data.Coords; 
import org.micromanager.display.DisplayWindow;

import ij.ImagePlus;
import ij.io.FileSaver;

/**
 *
 * @author Dickinson Lab
 */
public class LaserLysisForm extends JFrame { 
    private final Studio gui_;
    private final mmcorej.CMMCore mmc_;    
    
    String[] none = {"<none>"};
    
    private final JLabel instruction1 = new JLabel("<html>Configure acquisition in MDA window before lysing your cell</html");
    
    // Initialize Stage controls
    private final JLabel labelY= new JLabel("<html>Distance to move in Y<br/>(along channel) before acquisition</html>");
    private final JFormattedTextField textField_Y= new JFormattedTextField();
    private final JLabel labelX= new JLabel("<html>Distance to move in X<br/>(corrects for non-vertical channel)</html>");
    private final JFormattedTextField textField_X= new JFormattedTextField();
    private final JLabel labeldt= new JLabel("<html>Time to wait before acquisition start</html>");
    private final JFormattedTextField textField_dt= new JFormattedTextField();
    private final JButton startButton = new JButton("Move & Start Acquisition");
    
    // Initialize fire button for pulsed IR laser
    private final JLabel instruction2 = new JLabel("<html>Pulsed IR Lysis Laser Conrols:</html>");
    private final ImageIcon laserIcon = new ImageIcon("C:/Program Files/Micro-Manager-2.0gamma/scripts/laser-icon-small.jpg");
    private final JButton fireButton = new JButton("Fire!", laserIcon);

    public LaserLysisForm(Studio gui) {
        gui_ = gui;
        mmc_ = gui_.core();
        makeDialog();
    }
    
    public final Runnable acquisition = new Runnable() {
        public void run() {
            gui_.acquisitions().runAcquisition();
        }
    };
    
    private void makeDialog() {
        // Set Default Values
        textField_Y.setValue(150);
        textField_Y.setColumns(4);
        textField_Y.setEditable(true);
        textField_X.setValue(0);
        textField_X.setColumns(4);
        textField_X.setEditable(true);
        textField_dt.setValue(5);
        textField_dt.setColumns(4);
        textField_dt.setEditable(true);
    
        // Arrange GUI window
        setTitle("Dynamic SiMPull Acquisition");
        setSize(400, 400);
        setLocation(0, 500);
        Container cp = getContentPane();
        cp.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10,10,10,10);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy = 0;
        cp.add(instruction1,c);
        c.gridy = 1;
        cp.add(labelY,c);
        c.gridx = 1;
        cp.add(textField_Y,c);
        c.gridx = 0;
        c.gridy = 2;
        cp.add(labelX,c);
        c.gridx = 1;
        cp.add(textField_X,c);
        c.gridx = 0;
        c.gridy = 3;
        cp.add(labeldt,c);
        c.gridx = 1;
        cp.add(textField_dt,c);
        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.CENTER;
        cp.add(startButton,c);
        c.gridy = 5;
        c.anchor = GridBagConstraints.LINE_START;
        cp.add(instruction2,c);
        c.gridy = 6;
        c.anchor = GridBagConstraints.CENTER;
        cp.add(fireButton,c);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setVisible(true);        
        
        // Acquire dynamic SiMPull data on button press
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent eText) {
                int yShift = Integer.parseInt(textField_Y.getText());
                int xShift = Integer.parseInt(textField_X.getText());
                int dt = Integer.parseInt(textField_dt.getText());
                
                // Move Stage
                try {
                    String XYStage = mmc_.getXYStageDevice();
                    double x0 = mmc_.getXPosition(XYStage);
                    double y0 = mmc_.getYPosition(XYStage);
                    mmc_.setXYPosition(XYStage, x0 + xShift, y0 + yShift);
                    mmc_.waitForSystem();
                    mmc_.sleep(1000*dt); //Wait specified time for system to settle after stage movement
                } 
                catch (Exception e) {
                    //TODO error handling
                }
                // Start acquisition
                Thread acq = new Thread(acquisition);
                acq.start();
            }
        });

        //Fire IR laser on Button press
        fireButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent eText) {
        	try {
                    mmc_.setProperty("Arduino-Switch", "State", 1);
                    mmc_.setProperty("561 Shutter", "OnOff", 1); //Even though this is called 561 shutter, it's just the Arduino.
                    mmc_.sleep(10);
                    mmc_.setProperty("561 Shutter", "OnOff", 0);
                    mmc_.setProperty("Arduino-Switch", "State", 16);
                }
                catch (Exception e) {
                    //TODO error handling
                }
            }
        });
       
    }

}
