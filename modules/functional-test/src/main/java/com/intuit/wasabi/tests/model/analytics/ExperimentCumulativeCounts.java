/*******************************************************************************
 * Copyright 2016 Intuit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.intuit.wasabi.tests.model.analytics;

import com.google.gson.GsonBuilder;
import com.intuit.wasabi.tests.model.ModelItem;
import com.intuit.wasabi.tests.library.util.serialstrategies.DefaultNameExclusionStrategy;
import com.intuit.wasabi.tests.library.util.serialstrategies.SerializationStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the experiment cumulative counts.
 */
public class ExperimentCumulativeCounts extends ModelItem {

    /** cumulative counts per day */
    public List<DailyCounts> days = new ArrayList<>();

    /** The serialization strategy for comparisons and JSON serialization. */
    private static SerializationStrategy serializationStrategy = new DefaultNameExclusionStrategy();

    @Override
    public String toString() {
        return new GsonBuilder().create().toJson(this);
    }

    @Override
    public void setSerializationStrategy(SerializationStrategy serializationStrategy) {
        ExperimentCumulativeCounts.serializationStrategy = serializationStrategy;
    }

    @Override
    public SerializationStrategy getSerializationStrategy() {
        return ExperimentCumulativeCounts.serializationStrategy;
    }
}
