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

package io.sweers.catchup.ui.logs;

import android.content.Context;
import android.support.annotation.DrawableRes;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.functions.Consumer;
import io.sweers.catchup.R;
import io.sweers.catchup.data.LumberYard.Entry;
import io.sweers.catchup.ui.BindableAdapter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LogAdapter extends BindableAdapter<Entry> implements Consumer<Entry> {
  private List<Entry> logs;

  public LogAdapter(Context context) {
    super(context);
    logs = Collections.emptyList();
  }

  public static @DrawableRes int backgroundForLevel(int level) {
    switch (level) {
      case Log.VERBOSE:
      case Log.DEBUG:
        return R.color.debug_log_accent_debug;
      case Log.INFO:
        return R.color.debug_log_accent_info;
      case Log.WARN:
        return R.color.debug_log_accent_warn;
      case Log.ERROR:
      case Log.ASSERT:
        return R.color.debug_log_accent_error;
      default:
        return R.color.debug_log_accent_unknown;
    }
  }

  public void setLogs(List<Entry> logs) {
    this.logs = new ArrayList<>(logs);
  }

  @Override public void accept(Entry entry) throws Exception {
    logs.add(entry);
    notifyDataSetChanged();
  }

  @Override public int getCount() {
    return logs.size();
  }

  @Override public Entry getItem(int i) {
    return logs.get(i);
  }

  @Override public long getItemId(int i) {
    return i;
  }

  @Override public View newView(LayoutInflater inflater, int position, ViewGroup container) {
    View view = inflater.inflate(R.layout.debug_logs_list_item, container, false);
    LogItemViewHolder viewHolder = new LogItemViewHolder(view);
    view.setTag(viewHolder);
    return view;
  }

  @Override public void bindView(Entry item, int position, View view) {
    LogItemViewHolder viewHolder = (LogItemViewHolder) view.getTag();
    viewHolder.setEntry(item);
  }

  static final class LogItemViewHolder {
    private final View rootView;
    @BindView(R.id.debug_log_level) TextView levelView;
    @BindView(R.id.debug_log_tag) TextView tagView;
    @BindView(R.id.debug_log_message) TextView messageView;

    public LogItemViewHolder(View rootView) {
      this.rootView = rootView;
      ButterKnife.bind(this, rootView);
    }

    public void setEntry(Entry entry) {
      rootView.setBackgroundResource(backgroundForLevel(entry.level));
      levelView.setText(entry.displayLevel());
      tagView.setText(entry.tag);
      messageView.setText(entry.message);
    }
  }
}
