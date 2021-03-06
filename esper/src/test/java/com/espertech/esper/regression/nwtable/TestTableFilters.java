/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.nwtable;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EPStatement;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.bean.SupportBean_S1;
import com.espertech.esper.support.client.SupportConfigFactory;
import com.espertech.esper.support.util.SupportMessageAssertUtil;
import junit.framework.TestCase;

public class TestTableFilters extends TestCase {
    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp() {
        epService = EPServiceProviderManager.getDefaultProvider(SupportConfigFactory.getConfiguration());
        epService.initialize();
        for (Class clazz : new Class[] {SupportBean.class, SupportBean_S0.class, SupportBean_S1.class}) {
            epService.getEPAdministrator().getConfiguration().addEventType(clazz);
        }
        listener = new SupportUpdateListener();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
    }

    public void tearDown() {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        listener = null;
    }

    public void testFilters() throws Exception {
        epService.getEPAdministrator().createEPL("create table MyTable(pkey string primary key, col0 int)");
        epService.getEPAdministrator().createEPL("insert into MyTable select theString as pkey, intPrimitive as col0 from SupportBean");

        for (int i = 0; i < 5; i++) {
            epService.getEPRuntime().sendEvent(new SupportBean("E" + i, i));
        }
        String[] fields = "col0".split(",");

        // test FAF filter
        EventBean[] events = epService.getEPRuntime().executeQuery("select col0 from MyTable(pkey='E1')").getArray();
        EPAssertionUtil.assertPropsPerRow(events, fields, new Object[][] {{1}});

        // test iterate
        EPStatement stmtIterate = epService.getEPAdministrator().createEPL("select col0 from MyTable(pkey='E2')");
        EPAssertionUtil.assertPropsPerRow(stmtIterate.iterator(), fields, new Object[][] {{2}});
        stmtIterate.destroy();

        // test subquery
        EPStatement stmtSubquery = epService.getEPAdministrator().createEPL("select (select col0 from MyTable(pkey='E3')) as col0 from SupportBean_S0");
        stmtSubquery.addListener(listener);
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0));
        assertEquals(3, listener.assertOneGetNewAndReset().get("col0"));
        stmtSubquery.destroy();

        // test join
        SupportMessageAssertUtil.tryInvalid(epService, "select col0 from SupportBean_S0, MyTable(pkey='E4')",
                "Error starting statement: Joins with tables do not allow table filter expressions, please add table filters to the where-clause instead [");
    }
}
