package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class ForestBackdrop extends View {
    private final Bitmap art;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();
    private final int theme;
    private final boolean animated;
    private final long start=android.os.SystemClock.uptimeMillis();
    ForestBackdrop(Context c,int theme,boolean animated){super(c);this.theme=theme;this.animated=animated;art=ThemeArt.bitmap(c,ThemeArt.SCENES[theme]);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();float scale=Math.max(w/art.getWidth(),h/art.getHeight());
        float bw=art.getWidth()*scale,bh=art.getHeight()*scale;target.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);p.setColor(Color.WHITE);c.drawBitmap(art,null,target,p);
        p.setColor(Color.WHITE);p.setShader(new LinearGradient(0,0,0,h,new int[]{0x90101c1b,0x00101c1b,0x10101c1b,0xd0101c1b},new float[]{0,.3f,.55f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        if(animated){
            float t=(android.os.SystemClock.uptimeMillis()-start)/1000f;
            for(int i=0;i<18;i++){
                float x=w*((i*.173f+.08f)%1)+(float)Math.sin(t*.28+i*2)*w*.035f;
                float y=h*((i*.237f+t*(theme==1?.008f:-.004f)+10)%1);
                int a=(int)(70+65*Math.sin(t+i));p.setColor((a<<24)|(theme==2?0xa6fff1:0xffe7a5));
                if(theme==1){c.save();c.rotate(t*13+i*24,x,y);c.drawOval(x-4,y-8,x+4,y+8,p);c.restore();}
                else {c.drawCircle(x,y,theme==2?3:2,p);if(theme==2){p.setAlpha(20);c.drawCircle(x,y,10,p);p.setAlpha(255);}}
            }
            if(isShown() && hasWindowFocus())postInvalidateDelayed(40);
        }
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)invalidate();}
}

