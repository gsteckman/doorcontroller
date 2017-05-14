package io.github.gsteckman.doorcontroller;

/*
 * DoorApp.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License 
 * for the specific language governing permissions and limitations under the License.
 *
 */

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioProvider;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiBcmPin;
import com.pi4j.wiringpi.GpioUtil;

import io.github.gsteckman.rpi_ina219.INA219;
import io.github.gsteckman.rpi_rest.SubscriptionManager;

/**
 * This class serves as the application entry point and Spring Framework Boot configuration for the Door control
 * application.
 * 
 * @author Greg Steckman
 *
 */
@SpringBootApplication
public class DoorApp extends io.github.gsteckman.rpi_rest.App {
    private static final Log LOG = LogFactory.getLog(DoorApp.class);

    /**
     * Application entry point.
     * 
     * @param args
     *            Command line arguments.
     */
    public static void main(String[] args) {
        GpioUtil.enableNonPrivilegedAccess();
        SpringApplication app = new SpringApplication(DoorApp.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    /**
     * Creates a new DoorApp.
     */
    public DoorApp() {
    }

    /**
     * @param gp
     *            The GpioProvider to be used by the controller.
     * @return The GpioController to be used by the application.
     */
    @Bean
    @Override
    public GpioController gpioController(final GpioProvider gp) {
        GpioFactory.setDefaultProvider(gp);
        GpioController gpio = GpioFactory.getInstance();
        GpioPin pin4 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_04, PinState.LOW);
        pin4.setShutdownOptions(true, PinState.LOW);
        GpioPin pin17 = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_17, PinState.LOW);
        pin17.setShutdownOptions(true, PinState.LOW);
        return gpio;
    }

    /**
     * Creates a new INA219 driver with the following parameters:
     * <p>
     * <list>
     * <li>I2C address: 0x40
     * <li>Shunt resistance: 0.1 ohms
     * <li>Max expected current: 3.2 A
     * <li>Bus voltage range: 16 V
     * <li>Gain: 8x
     * <li>Bus ADC: 12 bits
     * <li>Shunt ADC: 128 samples </list>
     * 
     * @return The new INA219 instance.
     * @throws IOException
     *             If an I2C exception occured.
     */
    @Bean
    public INA219 ina219() throws IOException {
        return new INA219(INA219.Address.ADDR_40, 0.1, 3.2, INA219.Brng.V16, INA219.Pga.GAIN_8, INA219.Adc.BITS_12,
                INA219.Adc.SAMPLES_128);
    }

    /**
     * Creates a new SubscriptionManager for UPnP subscriptions.
     * @return The new SubscriptionManager.
     */
    @Bean
    public SubscriptionManager subscriptionManager() {
        return new SubscriptionManager();
    }

    /**
     * Creates the DoorController instance.
     * 
     * @param gc
     *            GpioController to be used by the DoorController.
     * @return The new DoorController.
     */
    @Bean
    public DoorController doorController( final GpioController gc, final INA219 ina219) {
        return new DoorController(gc, ina219);
    }

    /**
     * Creates and returns the DoorRestInteface bean.
     * 
     * @param dc
     *            The DoorController to be used by the rest interface.
     * @return A new DoorRestInterface.
     */
    @Bean
    public DoorRestInterface doorRestInterface(final SubscriptionManager sm, final DoorController dc) {
        return new DoorRestInterface(sm, dc);
    }
}
