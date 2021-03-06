package com.kelldavis.comickindle.model;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

@AutoValue
public abstract class Issue {
    public abstract long id();
    @Nullable
    public abstract String name();
    public abstract int issue_number();

    public static TypeAdapter<Issue> typeAdapter(Gson gson) {
        return new AutoValue_Issue.GsonTypeAdapter(gson);
    }

    public static Builder builder() {
        return new AutoValue_Issue.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder id(long id);
        public abstract Builder name(String name);
        public abstract Builder issue_number(int issue_number);

        public abstract Issue build();
    }
}

