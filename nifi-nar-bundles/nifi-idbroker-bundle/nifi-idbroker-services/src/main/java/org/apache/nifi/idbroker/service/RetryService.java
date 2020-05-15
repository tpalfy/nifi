/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.idbroker.service;

import org.apache.nifi.idbroker.domain.RetryableCommunicationException;
import org.apache.nifi.logging.ComponentLog;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class RetryService {
    private final ComponentLog logger;

    public RetryService(ComponentLog logger) {
        this.logger = logger;
    }

    public <R> R tryAction(Supplier<R> supplier, int maxTries, long waitBeforeRetryMs) {
        int counter = 1;

        while (true) {
            try {
                R result = supplier.get();

                return result;
            } catch (RetryableCommunicationException e) {
                if (++counter > maxTries) {
                    throw e;
                } else {
                    logger.error("Retryable action threw " + e.getClass().getSimpleName(), e);
                    try {
                        TimeUnit.MILLISECONDS.sleep(waitBeforeRetryMs);
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);
                    }
                }
            }
        }
    }
}
