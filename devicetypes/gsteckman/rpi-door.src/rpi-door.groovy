/**
 *  RPi Door Device Handler
 *
 *  Copyright 2017 Greg Steckman
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "RPi Door", namespace: "gsteckman", author: "Greg Steckman") {
		capability "Actuator"
		capability "Door Control"
		capability "Refresh"
		capability "Sensor"
	}

	tiles(scale: 2) {
		standardTile("door", "device.door", width: 6, height: 3, canChangeIcon: true, inactiveLabel: false, decoration: "flat") {
			state "open", label:'Open', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor: "#00a0dc", nextState: "closing"
            state "closed", label:'Closed', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor: "#ffffff", nextState: "opening"
            state "opening", label:'Opening', action:"door control.close", icon:"st.doors.garage.garage-open", backgroundColor: "#a0a0a0", nextState: "closing"
            state "closing", label:'Closing', action:"door control.open", icon:"st.doors.garage.garage-closed", backgroundColor: "#a0a0a0", nextState: "opening"
		}
      
      	childDeviceTiles("gpio")
        standardTile("refreshTile", "command.refresh", width: 3, height: 3, decoration: "ring") {
       	 	state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
    	}
        main("door")
        details(["door", "gpio", "refreshTile"])
	}
}

def installed(){
	log.debug "RPi Door Installed"
    createChildDevices();
    refresh(); 
}

def updated() {
	log.debug "RPi Door Updated"
    if (!childDevices) {
		createChildDevices()
	}
}

def subscribe(){
	log.debug "RPi Door Subscribe"

    //subscribe to door state changes
    def path = "/door/subscriptions"
    def callbackPath = ""
    log.trace "subscribe($path, $callbackPath)"
    
    def address = getCallBackAddress()
    def host = getHostAddress()

    def result = new physicalgraph.device.HubAction(
        method: "SUBSCRIBE",
        path: path,
        headers: [
            HOST: host,
            CALLBACK: "<http://${address}/notify$callbackPath>",
            NT: "upnp:event",
            TIMEOUT: "Second-90"
        ]
    )

    parent.sendHubCommand(result);
}


// parse events into attributes
def parse(String description) {
	def msg = parseLanMessage(description)
    log.debug "Parsed message: " + msg
    def bodyText = msg.body
    if(bodyText != null && (bodyText.length() > 0)){
    	def bodyMap = parseJson(bodyText)

        def result;
        if(bodyMap.name == "door"){
            result = createEvent(name: "door", value: bodyMap.state)
        }else{
        	//handle as generic GPIO pin child device
        	def childDevice = childDevices.find{it.deviceNetworkId == "${bodyMap.address}"}
            log.debug "found child device: " + childDevice
            if(childDevice.state.activeLow == true){
            	childDevice.sendEvent(name: "switch", value: bodyMap.state=="HIGH"? "off" : "on")
            }else{
               	childDevice.sendEvent(name: "switch", value: bodyMap.state=="HIGH"? "on" : "off")
            }
    	}

    	log.debug "Parse returned ${result?.descriptionText}"
    	return result
    }
}

def open() {
	log.debug "Executing 'open'"
    subscribe()
    return post("/door", [action: "open"])
}

def close() {
	log.debug "Executing 'close'"
    subscribe()
    return post("/door", [action: "close"])
}

def refresh() {
	log.debug "Executing 'refresh'"
    parent.sendHubCommand(get("/door"));
   
    childDevices.each{
    	it.refresh()
    }
}

def post(path, bodyMap){
	log.debug "POST"
	def address = convertHexToIP(getDataValue("ip"))+":"+convertHexToInt(getDataValue("port"))
	def result = new physicalgraph.device.HubAction(
    	method: "POST",
        path: path,
        HOST: address,
        headers: [
        	HOST: address
        ],
        body: bodyMap
 	)
    log.debug result
    return result;
}

def get(path){
	def address = convertHexToIP(getDataValue("ip"))+":"+convertHexToInt(getDataValue("port"))
	def result = new physicalgraph.device.HubAction(
    	method: "GET",
        path: path,
        HOST: address,
        headers: [
        	HOST: address
        ]
 	)
    return result;
}

def sync(ip, port) {
	def existingIp = getDataValue("ip")
	def existingPort = getDataValue("port")
	if (ip && ip != existingIp) {
		updateDataValue("ip", ip)
	}
	if (port && port != existingPort) {
		updateDataValue("port", port)
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

// gets the address of the device
private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

// gets the address of the Hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

private void createChildDevices() {
	log.debug "createChildDevices"
	state.oldLabel = device.label
	
    def device=addChildDevice("RPi GPIO Pin", "27", null,
                   [completedSetup: true, label: "27 Audio",
                    isComponent: true, componentName: "GPIO 27", componentLabel: "27 Audio"])
	device.setActiveLow();

    device=addChildDevice("RPi GPIO Pin", "22", null,
                   [completedSetup: true, label: "22 Lights",
                    isComponent: true, componentName: "GPIO 22", componentLabel: "22 Lights"])
	device.setActiveLow();
}