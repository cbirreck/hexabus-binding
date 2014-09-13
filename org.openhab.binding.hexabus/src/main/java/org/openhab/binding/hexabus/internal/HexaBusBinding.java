/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hexabus.internal;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Dictionary;
import java.util.List;

import org.openhab.binding.hexabus.HexaBusBindingProvider;

import org.apache.commons.lang.StringUtils;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.org.apache.xerces.internal.impl.dv.util.HexBin;
	
/**
 * @author cbirreck
 * @since 1.5.0
 */
public class HexaBusBinding extends AbstractActiveBinding<HexaBusBindingProvider> implements ManagedService {
	
	private static final Logger logger = LoggerFactory.getLogger(HexaBusBinding.class);

	private final OnOffType ON = OnOffType.ON;
	private final OnOffType OFF = OnOffType.OFF;

	// values acquired with wire shark
	private final String ON_MESSAGE  = "48583043040000000001010157b6";
	private final String OFF_MESSAGE = "485830430400000000010100463fff";
	private final String GET_MESSAGE = "48583043020000000002f7cb";
		
	private final int PLUG_PORT = 61616;
	private final int HEXABUS_RESPONSE_LENGTH = 79;
	
	//Socket information for the Jackdaw 6lowpan USB device
	private int jackdaw_port;
	private InetAddress jackdaw_ip;
	private DatagramSocket jackdaw_sock;
	
	// the standard refresh interval which is used to poll values from the HexaBus plug(s)
	// will be overwritten if a value is provided in the binding configuration
	private long refreshInterval = 60000;
	
	public void activate() {
		logger.debug("activate() is called.");
		
		
		// checks if the jackdaw_sock isn't created yet
		if (jackdaw_sock == null){
			try {
				jackdaw_sock = new DatagramSocket(jackdaw_port, jackdaw_ip);
			} catch (SocketException e) {
				e.printStackTrace();
				logger.debug("Could not create Jackdaw Socket in activate()!");
			}	
		}
	}
	
	public void deactivate() {
		// deallocate resources here that are no longer needed and 
		// should be reset when activating this binding again
		jackdaw_sock.close();
		jackdaw_sock = null; // probably redundant
	}

	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected long getRefreshInterval() {
		return refreshInterval;
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	protected String getName() {
		return "HexaBus Refresh Service";
	}
	
	/**
	 * This method is called at the start of the binding lifecycle (@TODO verify!)
	 * and each time the specified refreshInterval elapses.
	 * It is used to pull the power consumption of the HexabusPlug Plus.
-	 * 
	 */
	@Override
	protected void execute() {
		logger.debug("execute() method is called!");
		
		int val = -1;
		for (InetAddress target_ip : HexaBusGenericBindingProvider.getPullList()){
			val = getConsumption(target_ip);
			Command decimal = new DecimalType(val);
			eventPublisher.sendCommand("hpp_power", decimal);
		}
		//target_ip = InetAddress.getByName("acdc::50:c4ff:fe04:8310");

	}

	/**
	 * This method is called whenever a hexabus-item event is triggered.
	 * 
	 * @param itemName		the name of the hexabus item
	 * @param command		the corresponding command
	 */ 
	@Override
	protected void internalReceiveCommand(String itemName, Command command) {
		// the code being executed when a command was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveCommand() is called!");
				
		HexaBusBindingProvider provider = null;
		
		if (itemName != null){
			logger.debug("Received command (item ='{}', state='{}', class='{}')", 
					new Object[] {itemName, command.toString(), command.getClass().toString()});
			provider = findBindingProvider(itemName);
		}
				
		if (provider != null){
			// from .items-file; IDs have no functionality (for now)
			// int id = provider.getID(itemName);
			String type = provider.getPlugType(itemName);
			InetAddress ip = provider.getIP(itemName);

			if (type.equals("plug") || type.equals("plug+")){
				switchPlug(ip, command);
			}

		} else {
			logger.debug("Provider is empty!");
		}
	}
	
	/**
	 * Finds a binding provider for a given item. At the moment it checks
	 * if the item has a correctly defined plug type (aka not null) in the
	 * .items file.
	 * 
	 * @param itemName		the item to find a provider for
	 * @return 				reference to the provider
	 */
	private HexaBusBindingProvider findBindingProvider(String itemName){
		HexaBusBindingProvider provider = null;		
		logger.debug("findBindingProvider() called.");

		for (HexaBusBindingProvider pro : this.providers){
			String conf = pro.getPlugType(itemName);
			
			if (conf != null){
				provider = pro;
				break;
			}
		}
		return provider;
	}
	
	/**
	 * @{inheritDoc}
	 */
	@Override
	protected void internalReceiveUpdate(String itemName, State newState) {
		// the code being executed when a state was sent on the openHAB
		// event bus goes here. This method is only called if one of the 
		// BindingProviders provide a binding for the given 'itemName'.
		logger.debug("internalReceiveUpdate() is called!");
	}
		
	/**
	 * Parses the config file for Jackdaw Interface Values
	 */
	@Override
	public void updated(Dictionary<String, ?> config) throws ConfigurationException {
		logger.debug("updated() called.");
		
		// conditions for a proper configuration
		boolean refreshFlag		= false;
		boolean ipFlag			= false;
		boolean portFlag 		= false;
		
		if (config != null) {
			String refreshIntervalString = (String) config.get("refresh");
			
			if (StringUtils.isNotBlank(refreshIntervalString)) {
				try{
					refreshInterval = Long.parseLong(refreshIntervalString);
					refreshFlag = true;
				} catch (NumberFormatException e){
					logger.debug("Could not parse the refresh interval value from the configuration file in updated()!");
					e.printStackTrace();
				}
			}
			
			if (StringUtils.isNotBlank((String) config.get("ip"))){
				try {
					jackdaw_ip = InetAddress.getByName((String)config.get("ip"));
					ipFlag = true;
				} catch (UnknownHostException e) {
					logger.debug("IP address provided in configuration file is invalid! (Occured in updated())");
					e.printStackTrace();
				}
			} else {
				logger.debug("Hexabus device IP could not be parsed from .items-file. Might be blank.");
			}
			
			if (StringUtils.isNotBlank((String) config.get("port"))){
				try {
					jackdaw_port = Integer.parseInt(((String) config.get("port")));
					portFlag = true;
				} catch (NumberFormatException e) {
					logger.debug("Port provided in configuration file is invalid! (Occured in updated())");
					e.printStackTrace();
				}
			} else {
				logger.debug("Hexabus device port could not be parsed from .items-file. Might be blank.");
			}			
						
			String temp = "From config file: \n IP: " + jackdaw_ip.toString()
							+ "\n Port: " + jackdaw_port + "\n";
			logger.debug(temp);	
			
			// checks if requirements are met
			if(refreshFlag && ipFlag && portFlag){
				setProperlyConfigured(true);
			} else {
				setProperlyConfigured(false);	
			}
			
			testSwitch();
		}
	}
	
	/**
	 * Method to send UDP packets containing commands to the HexaBus Plugs
	 * 
	 * @param target  		Target IP address
	 * @param cmd 			A string which represents a command
	 */
	private void switchPlug(InetAddress target_ip, Command command){
		logger.debug("switchPlug() called!");
		
		jackdaw_sock.connect(target_ip, PLUG_PORT);
				
		String msg = "";
		if (command.equals(ON)) {
			msg = ON_MESSAGE;
		} else if (command.equals(OFF)){
			msg = OFF_MESSAGE;
		} else {
			logger.debug("Unrecognized cmd-String: " + command.toString());
			return;
		}
		byte[] bmsg = HexBin.decode(msg);
		logger.debug("InetAddress of Plug: " + target_ip);
		logger.debug("bmsg: " + bmsg.toString());
		DatagramPacket packet = new DatagramPacket(bmsg, bmsg.length);
		try {
			jackdaw_sock.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
			logger.debug("Could not send command packet in switchPlug()!");
		}
	}
	
	/**
	 * Gets the consumption of the targetted plug in Watt.
	 * 
	 * @param	target_ip 	the ip of the targetted plug
	 * 
	 * @return 	the power consumption in Watt
	 * @throws IOException
	 */
	private int getConsumption(InetAddress target_ip){				
		jackdaw_sock.connect(target_ip, PLUG_PORT);
		
		byte[] bmsg = HexBin.decode(GET_MESSAGE);
		byte[] receiveData = new byte[1024];
		
		DatagramPacket outgoing = new DatagramPacket(bmsg, bmsg.length);
		DatagramPacket incoming = new DatagramPacket(receiveData, HEXABUS_RESPONSE_LENGTH);

		
		try {
			jackdaw_sock.send(outgoing);
		} catch (IOException e) {
			logger.debug("Send failed on Jackdaw Socket while trying to GET consumption!");
			e.printStackTrace();
		}
		
		try {
			jackdaw_sock.receive(incoming);
		} catch (IOException e) {
			logger.debug("Receive failed on Jackdaw Socket while trying to GET consumption!");
			e.printStackTrace();
		}
		
		byte[] data = incoming.getData();
		
		// checks if no data was received.
		if (data.length == 0){
			logger.debug("No data was transmitted in getConsumption!");
		}
		
		// the last four bytes contain the consumption integer
		byte[] temp = {data[11], data[12], data[13], data[14]};
		ByteBuffer wrapped = ByteBuffer.wrap(temp);
		int val = wrapped.getInt();
		
		logger.debug("Power Consumption of hpp: " + val);
		return val;
	}
	
	/*
	 *  test-method; gets called in the beginning of the bindings life cycle
	 */
	private void testSwitch(){
		InetAddress pp = null;
		
		try {
			pp = InetAddress.getByName("acdc::50:c4ff:fe04:8310");
		} catch (UnknownHostException e) {
			e.printStackTrace();
			logger.debug("Could create InetAddress in testSwitch()!");
		}
		logger.debug("Attempting to send 'off' and 'on' packets...");
		switchPlug(pp, ON);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
			logger.debug("Interrupted in testSwitch()!");
		}
		switchPlug(pp, OFF);
	}
	
	/*
	 * test method
	 */
	private void testConsumption() 
			throws IOException, InterruptedException{
		InetAddress pp = InetAddress.getByName("acdc::50:c4ff:fe04:8310");
		logger.debug("Attempting to send 'get' packet...");
		getConsumption(pp);
	}
}
