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

package io.sweers.catchup.app;

import android.app.Application;
import android.content.Context;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import dagger.BindsInstance;
import dagger.Component;
import dagger.android.AndroidInjectionModule;
import io.sweers.catchup.data.DataModule;
import io.sweers.catchup.data.LumberYard;
import io.sweers.catchup.data.VariantDataModule;
import io.sweers.catchup.data.smmry.SmmryModule;
import io.sweers.catchup.injection.ConductorInjectionModule;
import io.sweers.catchup.injection.qualifiers.ApplicationContext;
import io.sweers.catchup.ui.activity.ActivityModule;
import okhttp3.OkHttpClient;

@Component(modules = {
    ActivityModule.class, AndroidInjectionModule.class, ApplicationModule.class,
    ConductorInjectionModule.class, DataModule.class, SmmryModule.class, VariantDataModule.class
})
public interface ApplicationComponent {

  void inject(CatchUpApplication application);

  LumberYard lumberYard();

  Application application();

  OkHttpClient okHttpClient();

  @ApplicationContext Context applicationContext();

  FirebaseRemoteConfig remoteConfig();

  @Component.Builder
  interface Builder {
    ApplicationComponent build();

    @BindsInstance Builder application(Application application);
  }
}
