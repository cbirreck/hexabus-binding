/**
 * Copyright (c) 2010-2014, openHAB.org and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.hexabus;

import java.net.InetAddress;

import org.openhab.core.binding.BindingProvider;

/**
 * @author cbirreck
 * @since 1.5.0
 */
public interface HexaBusBindingProvider extends BindingProvider {
	public int getID(String itemName);
	public String getPlugType(String itemName);
	public InetAddress getIP(String itemName);
}
