/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.openbravo.pos.util;

import com.handpoint.headstart.api.DeviceDiscoveryListener;
import com.handpoint.headstart.spi.pc.usb.UsbDiscovery;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.samuelcampos.usbdrivedectector.USBDeviceDetectorManager;
import net.samuelcampos.usbdrivedectector.USBStorageDevice;
import net.samuelcampos.usbdrivedectector.events.DeviceEventType;
import net.samuelcampos.usbdrivedectector.events.IUSBDriveListener;
import net.samuelcampos.usbdrivedectector.events.USBStorageEvent;
import net.samuelcampos.usbdrivedectector.process.CommandExecutor;

/**
 *
 * @author TM
 */
public class RemovableStorageHandler extends Observable {

    private final USBDeviceDetectorManager USB_MANAGER = new USBDeviceDetectorManager();
    //private final List<USBStorageDevice> REMOVABLEDEVICES = new ArrayList<>();
    private final OSValidator OS = new OSValidator();
    private final Set<USBStorageDevice> REMOVABLEDEVICES = new HashSet<>();
    private boolean isListning = false;

    private final USBDriveListener LISTNER = new USBDriveListener();
    private USBStorageDevice LAST_DEVICE;
    private static final RemovableStorageHandler INSTANCE = new RemovableStorageHandler();
    private final Pattern command1Pattern = Pattern.compile("^(\\/[^ ]+)[^%]+%[ ]+(.+)$");
    private final String unmount = "udisksctl unmount -b ";
    private final String power_off = "udisksctl power-off -b ";

    private RemovableStorageHandler() {
    }

    public static RemovableStorageHandler getInstance() {
        return INSTANCE;
    }

    public void registerUSBListner() {
        if (!isListning) {
            USB_MANAGER.addDriveListener(LISTNER);
            isListning = true;
        }

    }

    public void unregisterUSBListner() {
        if (isListning) {
            USB_MANAGER.removeDriveListener(LISTNER);
            isListning = false;
        }
    }

    public File getLatestUSBDevice() {
        if (LAST_DEVICE != null) {
            return LAST_DEVICE.getRootDirectory();
        } else {
            return null;
        }
    }

    public File[] getRemovableStorages() {
        if (REMOVABLEDEVICES.isEmpty()) {
            return null;
        }
        File[] storages = new File[REMOVABLEDEVICES.size()];
        int i = 0;
        for (USBStorageDevice dev : REMOVABLEDEVICES) {
            storages[i++] = dev.getRootDirectory();
        }
        return storages;
    }

    public boolean ejectDrive(USBStorageDevice dev) {
        if (OS.isWindows()) {
            try {
                CommandExecutor ex = new CommandExecutor("RunDll32.exe shell32.dll,Control_RunDLL hotplug.dll");

                ex.processOutput(new Consumer<String>() {
                    @Override
                    public void accept(String t) {
                        System.out.println("Output :" + t);
                    }
                });
                ex.close();
                return true;
            } catch (IOException ex1) {
                return false;
            }
        } else if (OS.isUnix()) {
            try {
                String device_name = getDevicePathLinux(dev);
                Process p_unmount = Runtime.getRuntime().exec(unmount + device_name);
                int ext_val = p_unmount.waitFor();
                if (ext_val == 0) {
                    Process p_power_down = Runtime.getRuntime().exec(power_off + device_name);
                    int exit_v = p_power_down.waitFor();
                    return exit_v == 0;
                }
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(RemovableStorageHandler.class.getName()).log(Level.SEVERE, null, ex);
            }

        }
        return false;
    }

    public boolean ejectDrive() {
        return ejectDrive(LAST_DEVICE);
    }

    private String getDevicePathLinux(final USBStorageDevice dev) {
        class Device {

            String dev_path;

            public String getDev_path() {
                return dev_path;
            }

            public void setDev_path(String dev_path) {
                this.dev_path = dev_path;
            }

        }
        Device usb_dev = new Device();
        try (CommandExecutor commandExecutor = new CommandExecutor("df")) {

            commandExecutor.processOutput((String output_line) -> {
                Matcher matcher = command1Pattern.matcher(output_line);

                if (matcher.matches()) {
                    String device = matcher.group(1);
                    String rootPath = matcher.group(2);

                    if (rootPath.equals(dev.getRootDirectory().getAbsolutePath())) {
                        usb_dev.setDev_path(device);
                    }
                }
            });

        } catch (IOException e) {

        }
        return usb_dev.getDev_path();
    }

    private class USBDriveListener implements IUSBDriveListener {

        public USBDriveListener() {
        }

        @Override
        public void usbDriveEvent(USBStorageEvent usbse) {
            if (usbse.getEventType() == DeviceEventType.CONNECTED && usbse.getStorageDevice().canWrite()) {

                REMOVABLEDEVICES.add(usbse.getStorageDevice());
                LAST_DEVICE = usbse.getStorageDevice();
                RemovableStorageHandler.this.setChanged();
                RemovableStorageHandler.this.notifyObservers(LAST_DEVICE);

            } else {
                REMOVABLEDEVICES.remove(usbse.getStorageDevice());
                if (LAST_DEVICE == usbse.getStorageDevice()) {
                    if (REMOVABLEDEVICES.size() > 0) {
                        LAST_DEVICE = REMOVABLEDEVICES.iterator().next();
                        RemovableStorageHandler.this.setChanged();
                        RemovableStorageHandler.this.notifyObservers(LAST_DEVICE);
                    } else {
                        LAST_DEVICE = null;
                        RemovableStorageHandler.this.setChanged();
                        RemovableStorageHandler.this.notifyObservers();
                    }
                }
            }

        }
    }

}
