/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package LaserLysis;

import org.micromanager.Studio;
import org.micromanager.MenuPlugin;
import org.scijava.plugin.SciJavaPlugin;
import org.scijava.plugin.Plugin;

/**
 *
 * @author Dickinson Lab
 */
@Plugin(type = MenuPlugin.class)
public class LaserLysis implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Laser Lysis";
   public static final String tooltipDescription =
      "Plugin for cell lysis and automated collection of dynamic SiMPull data";

   // Provides access to the Micro-Manager Java API (for GUI control and high-
   // level functions).
   private Studio gui_;
   // Provides access to the Micro-Manager Core API (for direct hardware
   // control)
   private LaserLysisForm myFrame_;

    @Override
    public void setContext(Studio si) {
      gui_ = si;
    }

    @Override
    public void onPluginSelected() {
        if (myFrame_ == null) {
            myFrame_ = new LaserLysisForm(gui_);
        }
        myFrame_.setVisible(true);
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getSubMenu() {
        return "Dickinson Lab Plugins";
    }

    @Override
    public String getHelpText() {
        return tooltipDescription;
    }   

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getCopyright() {
        return "Daniel J. Dickinson, 2021";
    }
    
}
