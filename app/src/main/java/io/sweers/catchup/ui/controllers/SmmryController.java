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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.v4.widget.NestedScrollView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.Unbinder;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.SimpleColorFilter;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.uber.autodispose.SingleScoper;
import dagger.Subcomponent;
import dagger.android.AndroidInjector;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import io.sweers.catchup.R;
import io.sweers.catchup.data.smmry.SmmryService;
import io.sweers.catchup.data.smmry.model.SmmryRequestBuilder;
import io.sweers.catchup.data.smmry.model.SmmryResponse;
import io.sweers.catchup.injection.ConductorInjection;
import io.sweers.catchup.rx.observers.adapter.SingleObserverAdapter;
import io.sweers.catchup.ui.base.ButterKnifeController;
import io.sweers.catchup.ui.base.ServiceController;
import io.sweers.catchup.ui.widget.ElasticDragDismissFrameLayout;
import io.sweers.catchup.ui.widget.ElasticDragDismissFrameLayout.ElasticDragDismissCallback;
import javax.inject.Inject;

/**
 * Overlay controller for displaying Smmry API results.
 */
public class SmmryController extends ButterKnifeController {

  private static final String ID_TITLE = "smmrycontroller.title";
  private static final String ID_URL = "smmrycontroller.url";
  private static final String ID_ACCENT = "smmrycontroller.accent";

  @Inject SmmryService smmryService;

  @BindView(R.id.loading_view) View loadingView;
  @BindView(R.id.progress) LottieAnimationView lottieView;
  @BindView(R.id.content_container) NestedScrollView content;
  @BindView(R.id.tags) TextView tags;
  @BindView(R.id.title) TextView title;
  @BindView(R.id.summary) TextView summary;
  @BindView(R.id.drag_dismiss_layout) ElasticDragDismissFrameLayout dragDismissFrameLayout;

  private String url;
  @ColorInt private int accentColor;
  private String fallbackTitle;

  private final ElasticDragDismissCallback dragDismissListener = new ElasticDragDismissCallback() {
    @Override public void onDragDismissed() {
      getRouter().popController(SmmryController.this);
      dragDismissFrameLayout.removeListener(this);
    }
  };

  public static <T> Consumer<T> showFor(ServiceController controller,
      String url,
      String fallbackTitle) {
    return t -> {
      // TODO Optimize this
      // Exclude images
      // Summarize reddit selftexts
      controller.getRouter()
          .pushController(RouterTransaction.with(new SmmryController(url,
              controller.getServiceThemeColor(),
              fallbackTitle))
              .pushChangeHandler(new VerticalChangeHandler(false))
              .popChangeHandler(new VerticalChangeHandler()));
    };
  }

  public SmmryController(Bundle args) {
    super(args);
  }

  public SmmryController(String url, @ColorInt int accentColor, String fallbackTitle) {
    this.url = url;
    this.accentColor = accentColor;
    this.fallbackTitle = fallbackTitle.trim();
  }

  @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(ID_TITLE, fallbackTitle);
    outState.putString(ID_URL, url);
    outState.putInt(ID_ACCENT, accentColor);
  }

  @Override protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    fallbackTitle = savedInstanceState.getString(ID_TITLE);
    url = savedInstanceState.getString(ID_URL);
    accentColor = savedInstanceState.getInt(ID_ACCENT);
  }

  @Override
  protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
    return inflater.inflate(R.layout.controller_smmry, container, false);
  }

  @Override protected void onViewBound(@NonNull View view) {
    super.onViewBound(view);
    final SimpleColorFilter colorFilter = new SimpleColorFilter(accentColor);
    lottieView.addColorFilter(colorFilter);
  }

  @Override protected void onAttach(@NonNull View view) {
    ConductorInjection.inject(this);
    super.onAttach(view);
    dragDismissFrameLayout.addListener(dragDismissListener);
    smmryService.summarizeUrl(SmmryRequestBuilder.forUrl(url)
        .withBreak(true)
        .keywordCount(5)
        .sentenceCount(5)
        .build())
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .to(new SingleScoper<>(this))
        .subscribe(new SingleObserverAdapter<SmmryResponse>() {
          @Override public void onSuccess(SmmryResponse value) {
            if (value.apiMessage() != null) {
              Toast.makeText(getActivity(),
                  "Smmry Error: " + value.errorCode() + " - " + value.apiMessage(),
                  Toast.LENGTH_LONG)
                  .show();
              getRouter().popController(SmmryController.this);
            } else {
              showSummary(value);
            }
          }

          @Override public void onError(Throwable e) {
            Toast.makeText(getActivity(), "API error", Toast.LENGTH_SHORT)
                .show();
            getRouter().popController(SmmryController.this);
          }
        });
  }

  @Override protected void onDetach(@NonNull View view) {
    dragDismissFrameLayout.removeListener(dragDismissListener);
    super.onDetach(view);
  }

  private void showSummary(SmmryResponse smmry) {
    if (smmry.keywords() != null) {
      tags.setTextColor(accentColor);
      tags.setText(TextUtils.join("  —  ",
          Observable.fromIterable(smmry.keywords())
              .map(s -> s.trim()
                  .toUpperCase())
              .toList()
              .blockingGet()));
      tags.setVisibility(View.VISIBLE);
    } else {
      tags.setVisibility(View.GONE);
    }
    String smmryTitle = smmry.title();
    if (TextUtils.isEmpty(smmryTitle)) {
      smmryTitle = fallbackTitle;
    }
    title.setText(smmryTitle);
    summary.setText(smmry.content()
        .replace("[BREAK]", "\n\n"));
    loadingView.animate()
        .alpha(0f)
        .setListener(new AnimatorListenerAdapter() {
          @Override public void onAnimationEnd(Animator animation) {
            loadingView.setVisibility(View.GONE);
            loadingView.animate()
                .setListener(null);
          }
        });
    content.setNestedScrollingEnabled(true);
    content.setAlpha(0f);
    content.setVisibility(View.VISIBLE);
    content.animate()
        .alpha(1f);
  }

  @Override protected Unbinder bind(@NonNull View view) {
    return new SmmryController_ViewBinding(this, view);
  }

  @Subcomponent
  public interface Component extends AndroidInjector<SmmryController> {
    @Subcomponent.Builder
    abstract class Builder extends AndroidInjector.Builder<SmmryController> {}
  }
}
