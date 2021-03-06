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

package io.sweers.catchup.data.designernews.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import io.sweers.catchup.ui.base.HasStableId;
import java.util.List;
import org.threeten.bp.Instant;

/**
 * Models a Designer News story
 */
@AutoValue
public abstract class Story implements HasStableId {

  public static JsonAdapter<Story> jsonAdapter(@NonNull Moshi moshi) {
    return new AutoValue_Story.MoshiJsonAdapter(moshi);
  }

  @Nullable public abstract String badge();

  @Nullable public abstract String comment();

  @Json(name = "comment_count") public abstract int commentCount();

  @Json(name = "comment_html") @Nullable public abstract String commentHtml();

  @Json(name = "created_at") public abstract Instant createdAt();

  @Nullable public abstract String hostname();

  public abstract String id();

  public abstract String href();

  public abstract String title();

  @Nullable public abstract String url();

  public abstract Links links();

  @Json(name = "vote_count") public abstract int voteCount();

  @Json(name = "twitter_handles") public abstract List<String> twitterHandles();

  @Override public long stableId() {
    return Long.parseLong(id());
  }
}
