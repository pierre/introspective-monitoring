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

import com.googlecode.jsendnsca.MessagePayload;
import com.googlecode.jsendnsca.NagiosException;
import com.googlecode.jsendnsca.PassiveCheckSender;
import com.googlecode.jsendnsca.builders.MessagePayloadBuilder;
import org.apache.log4j.Logger;
import org.skife.config.TimeSpan;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JsendnscaService implements MonitoredService, Runnable
{
    private static final Logger log = Logger.getLogger(JsendnscaService.class);

    private final String serviceName;
    private final ServiceCheck check;
    private final PassiveCheckSender sender;
    private final String hostname;
    private final ScheduledExecutorService executor;
    private final TimeSpan checkRate;

    public JsendnscaService(final String serviceName, final TimeSpan checkRate, final ServiceCheck check, final PassiveCheckSender sender, final MessagePayloadBuilder payloadBuilder)
    {
        this.serviceName = serviceName;
        this.checkRate = checkRate;
        this.check = check;
        this.sender = sender;
        // beware: payloadBuilder is not threadsafe, so can't just call withServiceName and be done with it
        this.hostname = payloadBuilder.create().getHostname();
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
            log.info(String.format("Service [%s] returned status [%s]", serviceName, status));
        }
        catch (RuntimeException e) {
            log.warn(String.format("Service [%s] threw exception", serviceName), e);
            status = ServiceCheck.Status.unknown("Check threw exception: " + e.getMessage());
        }

        if (status == null) {
            log.warn(String.format("Service [%s] returned null", serviceName));
            status = ServiceCheck.Status.unknown("Null status");
        }

        try {
            final MessagePayload payload = new MessagePayload(hostname, status.getLevel(), serviceName, status.getMessage());

            sender.send(payload);
        }
        catch (NagiosException e) {
            log.warn(String.format("Service [%s] had NagiosException sending status [%s]", serviceName, status), e);
        }
        catch (IOException e) {
            log.warn(String.format("Service [%s] had NagiosException sending status [%s]", serviceName, status), e);
        }
        catch (RuntimeException e) {
            log.warn(String.format("Service [%s] had NagiosException sending status [%s]", serviceName, status), e);
        }
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
