package com.googlecode.xm4was.jmx.mxbeans;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.googlecode.xm4was.jmx.resources.Messages;

public class PlatformMXBeansRegistrations {
    private static final Logger LOGGER = Logger.getLogger(PlatformMXBeansRegistrations.class.getName(), Messages.class.getName());
    
    private final MBeanServer mbs;
    private final AccessChecker accessChecker;
    private final List<ObjectName> registeredMBeans = new ArrayList<ObjectName>();

    public PlatformMXBeansRegistrations(MBeanServer mbs, AccessChecker accessChecker) {
        this.mbs = mbs;
        this.accessChecker = accessChecker;
    }

    /**
     * Register the given MBean and update {@link #registeredMBeans}.
     * 
     * @param object the MBean instance
     * @param name the MBean name
     */
    public void registerMBean(Object object, ObjectName name) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.entering("PlatformMXBeansRegistrations", "registerMBean", new Object[] { object, name });
        }
        try {
            mbs.registerMBean(new AccessControlProxy(object, name.getKeyProperty("type"), accessChecker), name);
            registeredMBeans.add(name);
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Registered MBean " + name + " (type " + object.getClass().getName() + ")");
            }
        } catch (JMException ex) {
            LOGGER.log(Level.SEVERE, "Failed to register MBean " + name + ": " + ex.getMessage());
        }
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.exiting("PlatformMXBeansRegistrations", "registerMBean", new Object[] { object, name });
        }
    }

    public int getMBeanCount() {
        return registeredMBeans.size();
    }
    
    public void unregisterMBeans() {
        if (LOGGER.isLoggable(Level.FINEST)) {
        	LOGGER.entering("PlatformMXBeansRegistrations", "unregisterMBeans");
        }
        for (ObjectName name : registeredMBeans) {
            try {
                mbs.unregisterMBean(name);
            } catch (JMException ex) {
                LOGGER.log(Level.SEVERE, "Failed to unregister MBean " + name + ": " + ex.getMessage());
            }
        }
        LOGGER.log(Level.INFO, Messages._0102I, new Object[] { String.valueOf(registeredMBeans.size()) });
        registeredMBeans.clear();
        if (LOGGER.isLoggable(Level.FINEST)) {
        	LOGGER.exiting("PlatformMXBeansRegistrations", "unregisterMBeans");
        }
    }
}
