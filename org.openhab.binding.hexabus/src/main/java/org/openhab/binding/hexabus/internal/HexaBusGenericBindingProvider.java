/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hexabus.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.binding.hexabus.HexaBusBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class is responsible for parsing the binding configuration.
 * 
 * Note that item substrings are split at ; and not :
 * 
 * Examples for valid config strings:
 * 
 * hexabus="1;plug;acdc::50:c4ff:fe04:8431"
 * hexabus="2;plug+;acdc::50:c4ff:fe04:8310"
 * hexabus="3;plug+power;acdc::c4ff:fe04:8310"
 * 
 * @author cbirreck
 * @since 1.5.0
 */
public class HexaBusGenericBindingProvider extends AbstractGenericBindingProvider implements HexaBusBindingProvider {
	
	// The list of all addresses of HexaBusPlug Plus devices from which to pull data
	private static List<InetAddress> pullList = new ArrayList<InetAddress>();
	private Map<String, Item> items = new HashMap<String, Item>();
	private static final Logger logger = LoggerFactory.getLogger(HexaBusBinding.class);

	/**
	 * {@inheritDoc}
	 */
	public String getBindingType() {
		return "hexabus";
	}

	/**
	 * @{inheritDoc}
	 */
	@Override
	public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
		if (!(item instanceof SwitchItem 
				|| item instanceof NumberItem)) {			
			
			throw new BindingConfigParseException("item '" + item.getName()
					+ "' is of type '" + item.getClass().getSimpleName()
					+ "', only Switch- and NumberItems are allowed - please check your *.items configuration");
		}
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processBindingConfiguration(String context, Item item, String bindingConfig)
			throws BindingConfigParseException {
		super.processBindingConfiguration(context, item, bindingConfig);
		
		// primitive parsing
		String[] confparts = bindingConfig.trim().split(";");
		int id = -1;
		
		try {
			id = Integer.parseInt(confparts[0]);
		} catch (NumberFormatException e){
			logger.debug("Could not parse the id value from the items-file in processBindingConfiguration()!");
			e.printStackTrace();
		}
		
		String type = confparts[1];
		String ip = confparts[2];
		
		HexaBusBindingConfig config = null;
		try {
			config = new HexaBusBindingConfig(id, type, InetAddress.getByName(ip));
		} catch (UnknownHostException e) {
			logger.debug("Could not parse the device ip from the items-file in processBindingConfiguration()!");
			e.printStackTrace();
			return;
		}
		
		addBindingConfig(item, config);
		items.put(item.getName(), item);
		
		if (config.getPlugType().equals("plug+")){
			logger.debug("BindProv ip string: " + config.getIP());
			pullList.add(config.getIP());
		}
	}
	
	/**
	 * Anonymous class which instances represent hexabus binding configs
	 * 
	 * @author cbirreck
	 */
	class HexaBusBindingConfig implements BindingConfig {
		private int id;
		private String plugType;
		private InetAddress ip;
		
		public HexaBusBindingConfig(int id, String plugType, InetAddress ip){
			this.id = id;
			this.plugType = plugType;
			this.ip = ip;
		}
		
		public int getID(){
			return id;
		}
		
		public String getPlugType(){
			return plugType;
		}
		
		public InetAddress getIP(){
			return ip;
		}
	}
	
	public Item getItem(String itemName){
		return items.get(itemName);
	}
	
	public HexaBusBindingConfig getHexaBusBindingConfig(String itemName) {
		return (HexaBusBindingConfig) this.bindingConfigs.get(itemName);
	}

	@Override
	public String getPlugType(String itemName) {
		HexaBusBindingConfig config = (HexaBusBindingConfig) bindingConfigs.get(itemName);
		return config.getPlugType();
	}

	@Override
	public int getID(String itemName) {
		HexaBusBindingConfig config = (HexaBusBindingConfig) bindingConfigs.get(itemName);
		return config.getID();
	}

	@Override
	public InetAddress getIP(String itemName) {
		HexaBusBindingConfig config = (HexaBusBindingConfig) bindingConfigs.get(itemName);
		return config.getIP();
	}
	
	public static List<InetAddress> getPullList(){
		return pullList;
	}
}
