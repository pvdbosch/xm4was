package com.googlecode.xm4was.jmx.mxbeans;

import java.util.HashMap;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;
import javax.management.StandardMBean;

public class AccessControlProxy implements DynamicMBean {
    private final Map<String,String> attributeNameToGetterNameMap = new HashMap<String,String>();
    private final Map<String,String> attributeNameToSetterNameMap = new HashMap<String,String>();
    
    private final DynamicMBean target;
    private final String type;
    private final AccessChecker accessChecker;
    
    public AccessControlProxy(Object target, String type, AccessChecker accessChecker) {
        if (target instanceof DynamicMBean)
        {
            this.target = (DynamicMBean)target;
        }
        else
        {
            this.target = new StandardMBean(target, null, true);
        }
        this.type = type;
        this.accessChecker = accessChecker;
        for (MBeanAttributeInfo info : this.target.getMBeanInfo().getAttributes()) {
            String name = info.getName();
            attributeNameToGetterNameMap.put(name, info.isIs() ? "is" + name : "get" + name);
            attributeNameToSetterNameMap.put(name, "set" + name);
        }
    }
    
    private void checkAccess(String methodName) {
        accessChecker.checkAccess(type + "." + methodName);
    }
    
    private void checkAttributeAccess(String attribute, Map<String,String> attributeNameToMethodNameMap) {
        String methodName = attributeNameToMethodNameMap.get(attribute);
        // If the attribute is unknown, let the target throw an appropriate exception
        if (methodName != null) {
            checkAccess(methodName);
        }
    }
    
    private void checkAttributeReadAccess(String attribute) {
        checkAttributeAccess(attribute, attributeNameToGetterNameMap);
    }
    
    private void checkAttributeWriteAccess(String attribute) {
        checkAttributeAccess(attribute, attributeNameToSetterNameMap);
    }
    
    public MBeanInfo getMBeanInfo() {
        return target.getMBeanInfo();
    }

    public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        checkAttributeReadAccess(attribute);
        return target.getAttribute(attribute);
    }

    public AttributeList getAttributes(String[] attributes) {
        for (String attribute : attributes) {
            checkAttributeReadAccess(attribute);
        }
        return target.getAttributes(attributes);
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        checkAttributeWriteAccess(attribute.getName());
        target.setAttribute(attribute);
    }

    public AttributeList setAttributes(AttributeList attributes) {
		for (Object attribute : attributes) {
			checkAttributeWriteAccess(((Attribute) attribute).getName());
        }
        return target.setAttributes(attributes);
    }

    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        checkAccess(actionName);
        return target.invoke(actionName, params, signature);
    }
}
