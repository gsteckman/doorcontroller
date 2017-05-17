package io.github.gsteckman.doorcontroller;

/*
 * INA219Util.java
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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.github.gsteckman.rpi_ina219.INA219;
import io.github.gsteckman.rpi_ina219.INA219.Address;

/**
 * This is a utility class to perform multiple reads of the current from the INA219.
 * 
 * @author Greg Steckman
 *
 */
public class INA219Util {
    private static final Log LOG = LogFactory.getLog(INA219Util.class);

    /**
     * Reads the Current from the INA219 with an I2C address and for a duration specified on the command line.
     * 
     * @param args
     *            Command line arguments.
     * @throws IOException
     *             If an error occurs reading/writing to the INA219
     * @throws ParseException
     *             If the command line arguments could not be parsed.
     */
    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("addr", true, "I2C Address");
        options.addOption("d", true, "Acquisition duration, in seconds");
        options.addOption("bv", false, "Also read bus voltage");
        options.addOption("sv", false, "Also read shunt voltage");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        Address addr = Address.ADDR_40;
        if (cmd.hasOption("addr")) {
            int opt = Integer.parseInt(cmd.getOptionValue("addr"), 16);
            Address a = Address.getAddress(opt);
            if (a != null) {
                addr = a;
            } else {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("INA219Util", options);
                return;
            }
        }

        int duration = 0;
        if (cmd.hasOption("d")) {
            String opt = cmd.getOptionValue("d");
            duration = Integer.parseInt(opt);
        } else {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("INA219Util", options);
            return;

        }

        boolean readBusVoltage = false;
        if (cmd.hasOption("bv")) {
            readBusVoltage = true;
        }

        boolean readShuntVoltage = false;
        if (cmd.hasOption("sv")) {
            readShuntVoltage = true;
        }

        INA219 i219 = new INA219(addr, 0.1, 3.2, INA219.Brng.V16, INA219.Pga.GAIN_8, INA219.Adc.BITS_12,
                INA219.Adc.SAMPLES_128);

        System.out.printf("Time\tCurrent");
        if (readBusVoltage) {
            System.out.printf("\tBus");
        }
        if (readShuntVoltage) {
            System.out.printf("\tShunt");
        }
        System.out.printf("\n");
        long start = System.currentTimeMillis();
        do {
            try {
                System.out.printf("%d\t%f", System.currentTimeMillis() - start, i219.getCurrent());
                if (readBusVoltage) {
                    System.out.printf("\t%f", i219.getBusVoltage());
                }
                if (readShuntVoltage) {
                    System.out.printf("\t%f", i219.getShuntVoltage());
                }
                System.out.printf("\n");
                Thread.sleep(100);
            } catch (IOException e) {
                LOG.error("Exception while reading I2C bus", e);
            } catch (InterruptedException e) {
                break;
            }
        } while (System.currentTimeMillis() - start < duration * 1000);
    }
}
