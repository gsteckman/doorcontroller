package io.github.gsteckman.doorcontroller;

/*
 * DoorController.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 *
 */

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.RaspiBcmPin;

import io.github.gsteckman.rpi_ina219.INA219;

/**
 * This class implements a DoorController utilizing pins 4 and 17 of the Raspberry Pi GPIO connected to an external
 * circuit with a DPDT latching relay and INA219 current monitor. Access to the GPIO are synchronized on the
 * GpioController object so as to avoid concurrent access from multiple threads. Other users of the GpioController
 * instance should take care to prevent concurrent access with this class by also synchronizing on the object or with
 * other suitable mechanisms.
 *
 */
public class DoorController {
    private static final Log LOG = LogFactory.getLog(DoorController.class);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private GpioController gpio;
    private INA219 ina219;
    private DoorState doorState = DoorState.CLOSED;
    private GpioPinDigitalOutput pin4;
    private GpioPinDigitalOutput pin17;
    private Thread monitorThread = null;

    /**
     * Creates a new DoorController.
     * 
     * @param gpioCtrl
     *            GpioController to be used for activating the door.
     * @param ina219
     *            INA219 current monitor interface for detecting when the door finishes moving.
     */
    public DoorController(final GpioController gpioCtrl, final INA219 ina219) {
        gpio = gpioCtrl;
        this.ina219 = ina219;
        pin4 = (GpioPinDigitalOutput) gpio.getProvisionedPin(RaspiBcmPin.GPIO_04);
        pin17 = (GpioPinDigitalOutput) gpio.getProvisionedPin(RaspiBcmPin.GPIO_17);
    }

    /**
     * Opens the door by pulsing GPIO pin 4 for 100 ms.
     */
    public synchronized void openDoor() {
        // Terminate the monitor thread before proceeding
        if (monitorThread != null) {
            monitorThread.interrupt();
            try {
                monitorThread.join();
            } catch (InterruptedException e) {
                // This is unlikely to happen, but if so abort operation to avoid races
                return;
            }
        }

        // Pulse GPIO to actuate door
        synchronized (gpio) {
            pin4.setState(false);
            pin17.setState(false);
            pin4.pulse(100, true);
        }

        // update state and start monitor thread
        setState(DoorState.OPENING);
        monitorThread = new Thread(new DoorMonitor());
        monitorThread.start();
    }

    /**
     * Closes the door by pulsing GPIO pin 17 for 100 ms.
     */
    public synchronized void closeDoor() {
        // Terminate the monitor thread before proceeding
        if (monitorThread != null) {
            monitorThread.interrupt();
            try {
                monitorThread.join();
            } catch (InterruptedException e) {
                // This is unlikely to happen, but if so abort operation to avoid races
                return;
            }
        }

        // Pulse GPIO to actuate door
        synchronized (gpio) {
            pin4.setState(false);
            pin17.setState(false);
            pin17.pulse(100, true);
        }

        // update state and start monitor thread
        setState(DoorState.CLOSING);
        monitorThread = new Thread(new DoorMonitor());
        monitorThread.start();
    }

    /**
     * Adds a listener for changes to the door state.
     * 
     * @param listener
     *            Listener for door state changes.
     */
    public void addPropertyChangeListener(final PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a listener from being notified of door state changes.
     * 
     * @param listener
     *            Listener to be removed.
     */
    public void removePropertyChangeListener(final PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * @return The current door state.
     */
    public DoorState getState() {
        return doorState;
    }

    /**
     * Enumeration for the possible door states. Under normal operation the door transitions as follows: <br>
     * CLOSED -> OPENING -> OPEN -> CLOSING -> CLOSED <br>
     * If the closeDoor method is called while the door is opening, it will transition to closing, and vice-versa for
     * the openDoor method.
     */
    public enum DoorState {
        OPEN, CLOSED, OPENING, CLOSING;
    }

    /**
     * This method sets the door's state to the provided value and notifies any listeners of the state change.
     * 
     * @param newState
     *            New state of the door.
     */
    private void setState(final DoorState newState) {
        DoorState oldState = doorState;
        doorState = newState;
        pcs.firePropertyChange("state", oldState, newState);
    }

    /**
     * Class to monitor the current running through the door actuator and update the door state to closed or open when
     * it is finished moving.
     */
    private class DoorMonitor implements Runnable {
        private static final double CURRENT_THRESHOLD = 0.1; // actuation in progress if current above this level
        private static final long MAX_ACTUATION_TIME = 50000; // door actuation should complete within 50 seconds based
                                                              // on measurements
        private long startTime = 0; // time at which the thread was started
        private DoorState state; // door state at creation. Should be Opening or Closing.
        private boolean actuatorStarted = false; // sets to true when current above threshold is detected

        /**
         * Create a new instance.
         */
        private DoorMonitor() {
            state = doorState; // capture state at creation
        }

        /**
         * Monitors the door actuator current for a transition from active (current above threshold) to inactive
         * (current below threshold). When this falling-edge transition occurs, set the state to OPEN or CLOSED
         * depending on if the door was opening or closing. Also checks for actuation time exceeding the maximum in
         * MAX_ACTUATION_TIME and if so sets the state to OPEN or CLOSED.
         */
        public void run() {
            startTime = System.currentTimeMillis();
            do {
                try {
                    Thread.sleep(100); // wait 100 ms before reading current
                } catch (InterruptedException e) {
                    return; // exit thread without changing state
                }
                try {
                    double current = ina219.getCurrent();

                    if (current > CURRENT_THRESHOLD) {
                        actuatorStarted = true;
                    }

                    if (current < CURRENT_THRESHOLD && actuatorStarted) { // detected falling edge of current
                        // motion stopped
                        if (state == DoorState.OPENING) {
                            setState(DoorState.OPEN);
                        } else if (state == DoorState.CLOSING) {
                            setState(DoorState.CLOSED);
                        }
                        return;
                    } else {
                        // check timeout condition.
                        if (System.currentTimeMillis() - startTime > MAX_ACTUATION_TIME) {
                            if (state == DoorState.OPENING) {
                                setState(DoorState.OPEN);
                            } else if (state == DoorState.CLOSING) {
                                setState(DoorState.CLOSED);
                            }
                            return;
                        }
                    }
                } catch (IOException e) {
                    LOG.error("Error reading INA219 current.", e);
                }
            } while (!Thread.interrupted());
        }
    }
}
