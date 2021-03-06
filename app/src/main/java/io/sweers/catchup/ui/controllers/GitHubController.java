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
import android.support.v4.util.Pair;
import android.view.ContextThemeWrapper;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Field;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.cache.normalized.CacheControl;
import com.apollographql.apollo.cache.normalized.CacheKey;
import com.apollographql.apollo.cache.normalized.CacheKeyResolver;
import com.apollographql.apollo.cache.normalized.NormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.lru.EvictionPolicy;
import com.apollographql.apollo.cache.normalized.lru.LruNormalizedCacheFactory;
import com.apollographql.apollo.cache.normalized.sql.ApolloSqlHelper;
import com.apollographql.apollo.cache.normalized.sql.SqlNormalizedCacheFactory;
import com.apollographql.apollo.rx2.Rx2Apollo;
import com.bluelinelabs.conductor.Controller;
import com.uber.autodispose.CompletableScoper;
import dagger.Binds;
import dagger.Lazy;
import dagger.Provides;
import dagger.Subcomponent;
import dagger.android.AndroidInjector;
import dagger.multibindings.IntoMap;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.sweers.catchup.BuildConfig;
import io.sweers.catchup.R;
import io.sweers.catchup.data.AuthInterceptor;
import io.sweers.catchup.data.HttpUrlApolloAdapter;
import io.sweers.catchup.data.ISO8601InstantApolloAdapter;
import io.sweers.catchup.data.LinkManager;
import io.sweers.catchup.data.github.GitHubSearch;
import io.sweers.catchup.data.github.GitHubSearch.Data;
import io.sweers.catchup.data.github.GitHubSearch.Languages;
import io.sweers.catchup.data.github.GitHubSearch.Node;
import io.sweers.catchup.data.github.GitHubSearch.Node1;
import io.sweers.catchup.data.github.TrendingTimespan;
import io.sweers.catchup.data.github.model.Repository;
import io.sweers.catchup.data.github.model.SearchQuery;
import io.sweers.catchup.data.github.model.User;
import io.sweers.catchup.data.github.type.CustomType;
import io.sweers.catchup.data.github.type.LanguageOrder;
import io.sweers.catchup.data.github.type.LanguageOrderField;
import io.sweers.catchup.data.github.type.OrderDirection;
import io.sweers.catchup.injection.ControllerKey;
import io.sweers.catchup.injection.qualifiers.ApplicationContext;
import io.sweers.catchup.ui.base.BaseNewsController;
import io.sweers.catchup.util.collect.Lists;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Qualifier;
import okhttp3.OkHttpClient;

public final class GitHubController extends BaseNewsController<Repository> {

  @Inject ApolloClient apolloClient;
  @Inject LinkManager linkManager;

  public GitHubController() {
    super();
  }

  public GitHubController(Bundle args) {
    super(args);
  }

  @Override protected Context onThemeContext(@NonNull Context context) {
    return new ContextThemeWrapper(context, R.style.CatchUp_GitHub);
  }

  @Override
  protected void bindItemView(@NonNull Repository item, @NonNull NewsItemViewHolder holder) {
    holder.hideComments();
    holder.title(item.fullName());
    holder.score(Pair.create("★", item.starsCount()));
    holder.timestamp(item.createdAt());
    holder.author(item.owner()
        .login());
    holder.source(null);
    holder.tag(item.language());
    holder.itemClicks()
        .compose(transformUrlToMeta(item.htmlUrl()))
        .flatMapCompletable(linkManager)
        .to(new CompletableScoper(holder))
        .subscribe();
  }

  @NonNull @Override protected Single<List<Repository>> getDataSingle(DataRequest request) {
    setMoreDataAvailable(false);
    String query = SearchQuery.builder()
        .createdSince(TrendingTimespan.WEEK.createdSince())
        .minStars(50)
        .build()
        .toString();

    //noinspection ConstantConditions it's not null here
    ApolloCall<Data> searchQuery = apolloClient.query(new GitHubSearch(query,
        50,
        LanguageOrder.builder()
            .direction(OrderDirection.DESC)
            .field(LanguageOrderField.SIZE)
            .build()))
        .cacheControl(
            request.fromRefresh() ? CacheControl.NETWORK_FIRST : CacheControl.CACHE_FIRST);

    return Rx2Apollo.from(searchQuery)
        .map(Response::data)
        .flatMap(data -> Observable.fromIterable(Lists.emptyIfNull(data.search()
            .nodes()))
            .map(Node::asRepository)
            .map(node -> {
              String primaryLanguage = null;
              Languages langs = node.languages();
              if (langs != null && langs.nodes() != null) {
                List<Node1> nodes = langs.nodes();
                if (nodes != null && !nodes.isEmpty()) {
                  primaryLanguage = nodes.get(0)
                      .name();
                }
              }
              return Repository.builder()
                  .createdAt(node.createdAt())
                  .fullName(node.name())
                  .htmlUrl(node.url()
                      .toString())
                  .id(node.id()
                      .hashCode())
                  .language(primaryLanguage)
                  .name(node.name())
                  .owner(User.create(node.owner()
                      .login()))
                  .starsCount(node.stargazers()
                      .totalCount())
                  .build();
            })
            .toList())
        .subscribeOn(Schedulers.io());
  }

  @Subcomponent
  public interface Component extends AndroidInjector<GitHubController> {

    @Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<GitHubController> {}
  }

  @dagger.Module(subcomponents = Component.class)
  public abstract static class Module {

    private static final String SERVER_URL = "https://api.github.com/graphql";

    @Qualifier
    private @interface InternalApi {}

    @Binds @IntoMap @ControllerKey(GitHubController.class)
    abstract AndroidInjector.Factory<? extends Controller> bindGitHubControllerInjectorFactory(
        Component.Builder builder);

    @Provides @InternalApi static OkHttpClient provideGitHubOkHttpClient(OkHttpClient client) {
      return client.newBuilder()
          .addInterceptor(AuthInterceptor.create("token", BuildConfig.GITHUB_DEVELOPER_TOKEN))
          .build();
    }

    @Provides static CacheKeyResolver provideCacheKeyResolver() {
      return new CacheKeyResolver() {
        @Nonnull @Override public CacheKey fromFieldRecordSet(@Nonnull Field field,
            @Nonnull Map<String, Object> objectSource) {
          //Specific id for User type.
          if (objectSource.get("__typename")
              .equals("User")) {
            String userKey = objectSource.get("__typename") + "." + objectSource.get("login");
            return CacheKey.from(userKey);
          }
          //Use id as default case.
          if (objectSource.containsKey("id")) {
            String typeNameAndIDKey = objectSource.get("__typename") + "." + objectSource.get("id");
            return CacheKey.from(typeNameAndIDKey);
          }
          return CacheKey.NO_KEY;
        }

        @Nonnull @Override public CacheKey fromFieldArguments(@Nonnull Field field,
            @Nonnull Operation.Variables variables) {
          return CacheKey.NO_KEY;
        }
      };
    }

    @Provides static NormalizedCacheFactory provideNormalizedCacheFactory(
        @ApplicationContext Context context) {
      ApolloSqlHelper apolloSqlHelper = new ApolloSqlHelper(context, "githubdb");
      return new LruNormalizedCacheFactory(EvictionPolicy.NO_EVICTION,
          new SqlNormalizedCacheFactory(apolloSqlHelper));
    }

    @Provides static ApolloClient provideApolloClient(@InternalApi final Lazy<OkHttpClient> client,
        NormalizedCacheFactory cacheFactory,
        CacheKeyResolver resolver) {
      return ApolloClient.builder()
          .serverUrl(SERVER_URL)
          .okHttpClient(client.get())
          .normalizedCache(cacheFactory, resolver)
          .addCustomTypeAdapter(CustomType.DATETIME, new ISO8601InstantApolloAdapter())
          .addCustomTypeAdapter(CustomType.URI, new HttpUrlApolloAdapter())
          .build();
    }
  }
}
