# LaserLysis
Micro-manager and Arduino code for laser-induced cell lysis and image acquisition. <br><br>
This code is used for Dynamic Single-cell, Single-molecule pull-down, described at <br>
https://biorxiv.org/cgi/content/short/2021.07.07.451367v1

Arduino Installation: <br>
Copy code into the Arduino IDE and send to device.

Building and Installation instructions for Micro-Manager plugin: 
see https://micro-manager.org/wiki/Writing_plugins_for_Micro-Manager for details. Here's the short version:

- Download and install Netbeans.  We have used version 8.0.2, availabe from https://netbeans.org/downloads/old/8.0/. Other versions may work but could have different configuration requirements. 

- Create a new project by selecting New > New Project... > Java > Java Class Library. 

-  Under the Projects tab, right-click your plugin project and choose Properties. Then choose Libraries > Compile > Add JAR/Folder. 
  - Browse to ..\Micro-Manager-2.0gamma\plugins\Micro-Manager and choose all jars in that directory. 
  - Click Add JAR/Folder again and add ..\Micro-Manager-2.0gamma\ij.jar
  
- Download the src/simpull_acquisition folder from this repository and copy it into the Source Packages directory of your netbeans project (visible under the projects tab on the left side of the Netbeans window). 

- From the menu choose Run > Clean and Build to compile the source code.  Copy the resulting .jar file into the ..\Micro-Manager-2.0gamma\mmplugins directory. 

- Launch Micro-Manager.  The acquisition control window should be visible when you select Plugins > Dickinson Lab Plugins > SiMPull Acquisition from the Micro-Manager menu.
