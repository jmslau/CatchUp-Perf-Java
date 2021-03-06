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

package io.sweers.catchup.ui.controllers;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.view.ContextThemeWrapper;
import com.bluelinelabs.conductor.Controller;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.squareup.moshi.Moshi;
import com.uber.autodispose.CompletableScoper;
import com.uber.autodispose.ObservableScoper;
import dagger.Binds;
import dagger.Lazy;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import io.reactivex.Single;
import io.sweers.catchup.BuildConfig;
import io.sweers.catchup.R;
import io.sweers.catchup.data.EpochInstantJsonAdapter;
import io.sweers.catchup.data.LinkManager;
import io.sweers.catchup.data.reddit.RedditService;
import io.sweers.catchup.data.reddit.model.RedditLink;
import io.sweers.catchup.data.reddit.model.RedditObjectFactory;
import io.sweers.catchup.injection.ControllerKey;
import io.sweers.catchup.ui.base.BaseNewsController;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Qualifier;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.threeten.bp.Instant;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;

import static io.sweers.catchup.data.RemoteConfigKeys.SMMRY_ENABLED;

public final class RedditController extends BaseNewsController<RedditLink> {

  @Inject RedditService service;
  @Inject LinkManager linkManager;
  @Inject FirebaseRemoteConfig remoteConfig;

  @Nullable private String lastSeen = null;

  public RedditController() {
    super();
  }

  public RedditController(Bundle args) {
    super(args);
  }

  @Override protected Context onThemeContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.CatchUp_Reddit);
  }

  @Override
  protected void bindItemView(@NonNull RedditLink link, @NonNull NewsItemViewHolder holder) {
    holder.title(link.title());

    holder.score(Pair.create("+", link.score()));
    holder.timestamp(link.createdUtc());
    holder.author("/u/" + link.author());

    if (link.domain() != null) {
      holder.source(link.domain());
    } else {
      holder.source("self");
    }

    holder.comments(link.commentsCount());
    holder.tag(link.subreddit());

    holder.itemClicks()
        .compose(transformUrlToMeta(link.url()))
        .flatMapCompletable(linkManager)
        .to(new CompletableScoper(holder))
        .subscribe();

    if (remoteConfig.getBoolean(SMMRY_ENABLED)) {
      holder.itemLongClicks()
          .to(new ObservableScoper<>(holder))
          .subscribe(SmmryController.showFor(this, link.url(), link.title()));
    }
    holder.itemCommentClicks()
        .compose(transformUrlToMeta("https://reddit.com/comments/" + link.id()))
        .flatMapCompletable(linkManager)
        .to(new CompletableScoper(holder))
        .subscribe();
  }

  @NonNull @Override protected Single<List<RedditLink>> getDataSingle(DataRequest request) {
    return service.frontPage(25, lastSeen)
        .map((redditListingRedditResponse) -> {
          lastSeen = redditListingRedditResponse.data()
              .after();
          //noinspection CodeBlock2Expr,unchecked
          return (List<RedditLink>) redditListingRedditResponse.data()
              .children();
        });
  }

  @Subcomponent
  public interface Component extends AndroidInjector<RedditController> {

    @Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<RedditController> {}
  }

  @dagger.Module(subcomponents = Component.class)
  public abstract static class Module {

    @Qualifier
    private @interface InternalApi {}

    @Binds @IntoMap @ControllerKey(RedditController.class)
    abstract AndroidInjector.Factory<? extends Controller> bindRedditControllerInjectorFactory(
        Component.Builder builder);

    @InternalApi @Provides static Moshi provideMoshi(Moshi upstreamMoshi) {
      return upstreamMoshi.newBuilder()
          .add(RedditObjectFactory.getInstance())
          .add(Instant.class, new EpochInstantJsonAdapter(TimeUnit.SECONDS))
          .build();
    }

    @InternalApi @Provides static OkHttpClient provideRedditOkHttpClient(OkHttpClient client) {
      return client.newBuilder()
          .addNetworkInterceptor(chain -> {
            Request request = chain.request();
            HttpUrl url = request.url();
            request = request.newBuilder()
                .header("User-Agent", "CatchUp app by /u/pandanomic")
                .url(url.newBuilder()
                    .encodedPath(url.encodedPath() + ".json")
                    .build())
                .build();
            return chain.proceed(request);
          })
          .build();
    }

    @Provides
    static RedditService provideRedditService(@InternalApi final Lazy<OkHttpClient> client,
        RxJava2CallAdapterFactory rxJavaCallAdapterFactory,
        @InternalApi Moshi moshi) {
      Retrofit retrofit = new Retrofit.Builder().baseUrl(RedditService.ENDPOINT)
          .callFactory(request -> client.get()
              .newCall(request))
          .addCallAdapterFactory(rxJavaCallAdapterFactory)
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .validateEagerly(BuildConfig.DEBUG)
          .build();
      return retrofit.create(RedditService.class);
    }
  }
}
