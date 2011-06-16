/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.nagios;

import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.skife.config.TimeSpan;
import org.weakref.jmx.MBeanExporter;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User this class in development mode, to fake interactions with the Nagios server
 */
public class FakeNagiosMonitor implements ServiceMonitor
{
    private static final Logger log = Logger.getLogger(FakeNagiosMonitor.class);

    private final ConcurrentMap<String, MonitoredService> services = new ConcurrentHashMap<String, MonitoredService>();
    private final TimeSpan checkRate;

    private volatile MBeanExporter mbeanExporter = new MBeanExporter(ManagementFactory.getPlatformMBeanServer());

    @Inject
    public FakeNagiosMonitor(final TimeSpan checkRate)
    {
        this.checkRate = checkRate;
    }

    @Inject(optional = true)
    public void setMbeanExporter(final MBeanExporter mbeanExporter)
    {
        this.mbeanExporter = mbeanExporter;
    }

    @Override
    public MonitoredService registerServiceCheck(final String serviceName, final ServiceCheck check)
    {
        final MonitoredService service = new FakeNagiosService(serviceName, checkRate, check);
        final MonitoredService existingService = services.putIfAbsent(serviceName, service);

        if (existingService != null) {
            throw new IllegalStateException(String.format("Service check [%s] has already been registered", serviceName));
        }

        log.info(String.format("Added service [%s] with check rate of [%s]", service, checkRate));

        if (mbeanExporter != null) {
            mbeanExporter.export(String.format("%s:name=%s", getClass().getPackage().getName(), serviceName), check);
        }

        return service;
    }

    private static class FakeNagiosService implements MonitoredService, Runnable
    {
        private static final Logger log = Logger.getLogger(FakeNagiosService.class);

        private final String serviceName;
        private final ServiceCheck check;
        private final ScheduledExecutorService executor;
        private final TimeSpan checkRate;

        private FakeNagiosService(final String serviceName, final TimeSpan checkRate, final ServiceCheck check)
        {
            this.serviceName = serviceName;
            this.checkRate = checkRate;
            this.check = check;
            this.executor = Executors.newScheduledThreadPool(1);
            this.executor.submit(this);
        }

        @Override
        public void run()
        {
            final long startNanos = System.nanoTime();

            try {
                report();
            }
            catch (Throwable t) {
                log.warn(String.format("Service [%s] had completely unexpected exception somewhere", serviceName), t);
            }
            finally {
                try {
                    final long ellapsedMillis = TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
                    final long delayMillis = checkRate.getMillis() - ellapsedMillis;

                    this.executor.schedule(this, delayMillis, TimeUnit.MILLISECONDS);
                }
                catch (Throwable t) {
                    log.error(String.format("Service [%s] had completely unexpected exception rescheduling", serviceName), t);
                }
            }
        }

        private void report()
        {
            ServiceCheck.Status status;

            try {
                status = check.checkServiceStatus();
            }
            catch (RuntimeException e) {
                log.warn(String.format("Service [%s] threw exception", serviceName), e);
                status = ServiceCheck.Status.unknown("Check threw exception: " + e.getMessage());
            }

            if (status == null) {
                log.warn(String.format("Service [%s] returned null", serviceName));
                status = ServiceCheck.Status.unknown("Null status");
            }

            log.info(String.format("Service [%s] would have reported: %s", serviceName, status));
        }

        @Override
        public String getServiceName()
        {
            return serviceName;
        }

        @Override
        public TimeSpan getCheckRate()
        {
            return checkRate;
        }

        @Override
        public String toString()
        {
            return serviceName;
        }
    }
}
