/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
public class RecursionManager {
  private static final ThreadLocal<Integer> ourStamp = new ThreadLocal<Integer>() {
    @Override
    protected Integer initialValue() {
      return 0;
    }
  };
  private static final ThreadLocal<LinkedHashMap<Pair<String, Object>, Integer>> ourProgress = new ThreadLocal<LinkedHashMap<Pair<String, Object>, Integer>>() {
    @Override
    protected LinkedHashMap<Pair<String, Object>, Integer> initialValue() {
      return new LinkedHashMap<Pair<String, Object>, Integer>();
    }
  };

  public static RecursionGuard createGuard(final String id) {
    return new RecursionGuard() {
      @Override
      public <T> T doPreventingRecursion(Object key, Computable<T> computation) {
        Pair<String, Object> realKey = Pair.create(id, key);
        LinkedHashMap<Pair<String, Object>, Integer> progressMap = ourProgress.get();
        if (progressMap.containsKey(realKey)) {
          prohibitResultCaching(key);

          return null;
        }

        progressMap.put(realKey, ourStamp.get());

        try {
          return computation.compute();
        }
        finally {
          ourStamp.set(progressMap.remove(realKey));
        }
      }

      @Override
      public StackStamp markStack() {
        final Integer stamp = ourStamp.get();
        return new StackStamp() {
          @Override
          public boolean mayCacheNow() {
            return Comparing.equal(stamp, ourStamp.get());
          }
        };
      }

      @Override
      public List<Object> currentStack() {
        ArrayList<Object> result = new ArrayList<Object>();
        LinkedHashMap<Pair<String, Object>, Integer> map = ourProgress.get();
        for (Pair<String, Object> pair : map.keySet()) {
          if (pair.first == id) {
            result.add(pair.second);
          }
        }
        return result;
      }

      @Override
      public void prohibitResultCaching(Object since) {
        int stamp = ourStamp.get() + 1;
        ourStamp.set(stamp);

        boolean inLoop = false;
        for (Map.Entry<Pair<String, Object>, Integer> entry: ourProgress.get().entrySet()) {
          if (inLoop) {
            entry.setValue(stamp);
          }
          else if (entry.getKey().first.equals(id) && entry.getKey().second.equals(since)) {
            inLoop = true;
          }
        }
      }
    };
  }

}