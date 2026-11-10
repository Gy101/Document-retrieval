package com.doccheck.check;

@FunctionalInterface
public interface ProgressCallback {

    void update(int progress, String stage);
}
