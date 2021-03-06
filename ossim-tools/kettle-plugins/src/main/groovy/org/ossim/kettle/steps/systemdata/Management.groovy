/*******************************************************************************
 *
 * Pentaho Data Integration
 *
 * Copyright (C) 2002-2012 by Pentaho : http://www.pentaho.com
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.ossim.kettle.steps.systemdata;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

import org.pentaho.di.core.Const;

import com.sun.management.OperatingSystemMXBean;

public class Management {
	private static RuntimeMXBean mx=null ;
	private static OperatingSystemMXBean bean=null;
	private static ThreadMXBean tbean = null;


    static String getOperatingSystemName()
    {
        if(!bean) bean = ManagementFactory.operatingSystemMXBean
        String result
        if(bean)
        {
            result = bean.name
        }
        result
    }
    static String getOperatingSystemVersion()
    {
        if(!bean) bean = ManagementFactory.operatingSystemMXBean
        String result
        if(bean)
        {
            result = bean.version
        }
        result
    }
    static String getOperatingSystemArch()
    {
        if(!bean) bean = ManagementFactory.operatingSystemMXBean
        String result
        if(bean)
        {
            result = bean.arch
        }
        result
    }
	/**
	 * Return infos like current PID, JVM memory, ...
	 * 
	 * @author Samatar
	 * @since 2008-apr-29
	 */
	
	 /** 
     * @return Process CPU time in nanoseconds 
     */
    public static long getPID()
    {
    	String pid=null;
    	if(mx==null)
    	{
    		mx = ManagementFactory.getRuntimeMXBean();
    	}
    		
		pid=mx.getName();
		int indexOf=pid.indexOf("@");
		if(indexOf>0) pid=pid.substring(0,indexOf);
        return Const.toInt(pid,-1);
    }
    /** 
     * @return JVM CPU time in nanoseconds 
     */
    public static long getJVMCpuTime( ) {
    	setOperatingSystemMXBean();
    	if ( ! (bean instanceof OperatingSystemMXBean) )
    		return 0L;
    	return ((OperatingSystemMXBean)bean).getProcessCpuTime();
    }
    /** 
     * @return the amount of free physical memory in bytes
     */
    public static long getFreePhysicalMemorySize() {
    	setOperatingSystemMXBean();

        if ( ! (bean instanceof OperatingSystemMXBean) )
            return 0L;
        return ((OperatingSystemMXBean)bean).getFreePhysicalMemorySize();
    }
    /** 
     * @return the amount of free swap space in bytes
     */
    public static long getFreeSwapSpaceSize() {
    	setOperatingSystemMXBean();

        if ( ! (bean instanceof OperatingSystemMXBean) )
            return 0L;
        return ((OperatingSystemMXBean)bean).getFreeSwapSpaceSize();
    }
    /** 
     * @return the total amount of physical memory in bytes
     */
    public static long getTotalPhysicalMemorySize() {
    	setOperatingSystemMXBean();

        if ( ! (bean instanceof OperatingSystemMXBean) )
            return 0L;
        return ((OperatingSystemMXBean)bean).getTotalPhysicalMemorySize( );
    }
    /** 
     * @return the total amount of swap space in bytes.
     */
    public static long getTotalSwapSpaceSize() {
    	setOperatingSystemMXBean();

        if ( ! (bean instanceof OperatingSystemMXBean) )
            return 0L;
        return ((OperatingSystemMXBean)bean).getTotalSwapSpaceSize();
    }
    
    /** 
     * @return the amount of virtual memory that is guaranteed to be available to the running process in bytes
     */
    public static long getCommittedVirtualMemorySize() {
    	setOperatingSystemMXBean();

        if ( ! (bean instanceof OperatingSystemMXBean) )
            return 0L;
        return ((OperatingSystemMXBean)bean).getCommittedVirtualMemorySize();
    }
    /**
     *  @return CPU time in nanoseconds. 
     */
   public static long getCpuTime( long id ) {
	   setThreadMXBean();
        if ( ! tbean.isThreadCpuTimeSupported( ) )
            return 0L;
        return  tbean.getThreadCpuTime( id );
    }
   
    private static void setOperatingSystemMXBean(){
    	if(bean==null){
            bean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean( );
    	}
    }
    private static void setThreadMXBean(){
    	if(tbean==null){
    		tbean = ManagementFactory.getThreadMXBean( );
    	}
    } 
}
