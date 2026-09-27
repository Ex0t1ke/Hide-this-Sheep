package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.SparseArray;

/** Shared decoded artwork: avoid allocating full-resolution images on every screen change. */
final class ThemeArt {
    static final String[] NAMES={"Лесная поляна", "Золотая осень", "Лунный лес"};
    static final String[] NOTES={"Тёплый свет · шелест травы", "Янтарные листья · тихий лес", "Светлячки · серебряная луна"};
    static final int[] SCENES={R.drawable.reference_loading,R.drawable.scene_autumn,R.drawable.scene_moon};
    static final int[] ACCENTS={0xffd9c58b,0xffe6b778,0xff9edadd};
    static final int[] LIGHT={0xff8ba653,0xffc79c52,0xff438b8d};
    static final int[] DARK={0xff456136,0xff795239,0xff204e60};
    private static final SparseArray<Bitmap> CACHE=new SparseArray<>();
    static synchronized Bitmap bitmap(Context c,int id) {
        Bitmap b=CACHE.get(id);
        if(b==null){BitmapFactory.Options o=new BitmapFactory.Options(); o.inScaled=false;
            if(id==R.drawable.sheep_painted || id==R.drawable.wolf_painted || id==R.drawable.sheep_cartoon || id==R.drawable.wolf_cartoon) o.inSampleSize=2;
            b=BitmapFactory.decodeResource(c.getResources(),id,o);CACHE.put(id,b);}
        return b;
    }
}

