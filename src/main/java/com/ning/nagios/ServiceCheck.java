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

import com.googlecode.jsendnsca.Level;

public interface ServiceCheck
{
    public Status checkServiceStatus();

    public static class Status
    {
        private final Level level;
        private final String message;

        private Status(final Level level, final String message)
        {
            this.level = level;
            this.message = message;
        }

        public static Status ok(final String message)
        {
            return new Status(Level.OK, message);
        }

        public static Status okf(final String format, final Object... args)
        {
            return new Status(Level.OK, String.format(format, args));
        }

        public static Status warning(final String message)
        {
            return new Status(Level.WARNING, message);
        }

        public static Status warningf(final String format, final Object... args)
        {
            return new Status(Level.WARNING, String.format(format, args));
        }

        public static Status critical(final String message)
        {
            return new Status(Level.CRITICAL, message);
        }

        public static Status criticalf(final String format, final Object... args)
        {
            return new Status(Level.CRITICAL, String.format(format, args));
        }

        public static Status unknown(final String message)
        {
            return new Status(Level.UNKNOWN, message);
        }

        public static Status unknownf(final String format, final Object... args)
        {
            return new Status(Level.UNKNOWN, String.format(format, args));
        }

        Level getLevel()
        {
            return level;
        }

        String getMessage()
        {
            return message;
        }

        @Override
        public String toString()
        {
            return level + " " + message;
        }
    }
}
