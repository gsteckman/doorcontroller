package io.github.gsteckman.doorcontroller;

/*
 * DoorRestInterface.java
 * 
 * Copyright 2017 Greg Steckman
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in 
 * compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is 
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing permissions and limitations under the License.
 *
 */

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.gsteckman.rpi_rest.SubscriptionManager;
import io.github.gsteckman.rpi_rest.SubscriptionManager.SubscriptionInfo;

/**
 * This class implements a REST interface to the Raspberry Pi for control of a door using the GPIO.
 * 
 * @author Greg Steckman
 */
@RestController
public class DoorRestInterface {
    private static final Log LOG = LogFactory.getLog(DoorRestInterface.class);
    private static final String SUBSCRIBE_METHOD = "SUBSCRIBE";
    private static final String UNSUBSCRIBE_METHOD = "UNSUBSCRIBE";
    private static final String SUBSCRIPTION_KEY = "/door/subscriptions";
    private DoorController dc;
    private SubscriptionManager subMgr;

    /**
     * Create a new DoorRestInterface using the provided SubscriptionManager and DoorController.
     * @param sm SubscriptionManager for supporting UPnP subscriptions.
     * @param doorController The DoorController to use for activating the door.
     */
    public DoorRestInterface(final SubscriptionManager sm, final DoorController doorController) {
        subMgr = sm;
        dc = doorController;

        dc.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent arg0) {
                ObjectMapper mapper = new ObjectMapper();
                try {
                    subMgr.fireEvent(SUBSCRIPTION_KEY, "application/JSON", mapper.writeValueAsString(getDoorState()));
                } catch (JsonProcessingException e) {
                    LOG.error(e);
                }
            }
        });
    }

    /**
     * This method handles the UPnP defined HTTP methods SUBSCRIBE and UNSUBSCRIBE bound to the URL path /door/subscriptions.
     * @param req The request provided from the servlet container.
     * @param res The response object provided by the servlet container.
     * @throws IOException Thrown by HttpServletResponse.sendError if an error occurs writing the response.
     */
    @RequestMapping("/door/subscriptions")
    public void process(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (SUBSCRIBE_METHOD.equals(req.getMethod())) {
            LOG.debug("SUBSCRIBE /door/subscriptions");
            subMgr.processSubscribe(SUBSCRIPTION_KEY, req, res);
        } else if (UNSUBSCRIBE_METHOD.equals(req.getMethod())) {
            LOG.debug("UNSUBSCRIBE /door/subscriptions");
            subMgr.processUnsubscribe(SUBSCRIPTION_KEY, req, res);
        }
    }

    /**
     * GET /door/subscriptions.
     * 
     * Returns all current subscribers to door state change events.
     *  
     * @return A collection of subscribers.
     */
    @GetMapping(path = "/door/subscriptions")
    public Collection<SubscriptionInfo> getDoorSubscriptions() {
        LOG.debug("GET /door/subscroptions");
        return subMgr.getSubscriptions(SUBSCRIPTION_KEY);
    }

    /**
     * POST /door REST endpoint to open/close the door.
     * 
     * Body
     * 
     * {action: "open" | "close"}
     * 
     * Response:
     * 
     * { name: "door", state: "open" | "closed" }
     * 
     * @param model
     *            JSON data model provided in the body of the POST
     */
    @PostMapping(path = "/door")
    public Map<String, Object> door(@RequestBody Map<String, Object> model) {
        LOG.debug("POST /door");
        String action = (String) model.get("action");
        if ("open".equals(action)) {
            dc.openDoor();
        } else {
            dc.closeDoor();
        }
        return getDoorState();
    }

    /**
     * 
     * Response:
     * 
     * {name: "door", state: "open" | "closed"}
     * 
     * @return Map of name/value pairs that Spring converts to a HTTP JSON Response
     */
    @GetMapping(path = "/door")
    public Map<String, Object> getDoorState() {
        LOG.debug("GET /door");

        Map<String, Object> returnMap = new HashMap<String, Object>();
        returnMap.put("name", "door");
        returnMap.put("state", dc.getState().toString());
        return returnMap;
    }
}
