/*
 * Copyright (c) 2017 Zac Sweers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sweers.catchup.rx;

import rx.Observer;
import timber.log.Timber;

public class OptimisticObserver<T> implements Observer<T> {

  private String tag;

  public OptimisticObserver(String tag) {
    this.tag = tag;
  }

  @Override
  public void onCompleted() {

  }

  @Override
  public void onError(Throwable e) {
    Timber.e(e, tag);
  }

  @Override
  public void onNext(T t) {

  }
}
