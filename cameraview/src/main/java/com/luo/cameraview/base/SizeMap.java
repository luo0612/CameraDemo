package com.luo.cameraview.base;

import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.ArrayMap;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class SizeMap {
    private final ArrayMap<AspectRatio, SortedSet<Size>> mRatios = new ArrayMap<AspectRatio, SortedSet<Size>>();

    public boolean add(Size size) {
        for (AspectRatio ratio : mRatios.keySet()) {
            if (ratio.matches(size)) {
                SortedSet<Size> sizes = mRatios.get(ratio);
                if (sizes.contains(size)) {
                    return false;
                } else {
                    sizes.add(size);
                    return true;
                }
            }
        }
        SortedSet<Size> sizes = new TreeSet<Size>();
        sizes.add(size);
        mRatios.put(AspectRatio.of(size.getWidth(), size.getHeight()), sizes);
        return true;
    }

    public void remove(AspectRatio ratio) {
        mRatios.remove(ratio);
    }

    public Set<AspectRatio> ratios() {
        return mRatios.keySet();
    }

    public SortedSet<Size> sizes(AspectRatio ratio) {
        return mRatios.get(ratio);
    }

    public void clear() {
        mRatios.clear();
    }

    public boolean isEmpty() {
        return mRatios.isEmpty();
    }
}
