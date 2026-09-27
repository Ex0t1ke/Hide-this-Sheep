package com.example.myapplication;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ViewGroup;
import android.widget.Button;

/** The reference illustration and its native buttons share exactly the same fit transform. */
final class IllustratedMenu extends ViewGroup {
    private final Bitmap image;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF artBounds=new RectF();
    private final Button play,settings;
    IllustratedMenu(Context context,Runnable onPlay,Runnable onSettings){
        super(context);setWillNotDraw(false);image=ThemeArt.bitmap(context,R.drawable.reference_main_menu);
        play=hotspot("Играть",onPlay);settings=hotspot("Настройки",onSettings);
        addView(play);addView(settings);
    }
    private Button hotspot(String label,Runnable action){
        Button b=new Button(getContext());b.setText(label);b.setTextColor(Color.TRANSPARENT);b.setContentDescription(label);
        b.setMinWidth(0);b.setMinHeight(0);b.setPadding(0,0,0,0);b.setStateListAnimator(null);
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(32*getResources().getDisplayMetrics().density);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x60fffbd0),null,mask));
        b.setOnClickListener(v->action.run());return b;
    }
    @Override protected void onMeasure(int w,int h){
        setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));
        float scale=Math.min(getMeasuredWidth()/(float)image.getWidth(),getMeasuredHeight()/(float)image.getHeight());
        float iw=image.getWidth()*scale,ih=image.getHeight()*scale;
        artBounds.set((getMeasuredWidth()-iw)/2,(getMeasuredHeight()-ih)/2,(getMeasuredWidth()+iw)/2,(getMeasuredHeight()+ih)/2);
        measureHotspot(play,.19f,.745f,.815f,.854f);measureHotspot(settings,.28f,.872f,.727f,.955f);
    }
    private void measureHotspot(Button b,float l,float t,float r,float bot){b.measure(MeasureSpec.makeMeasureSpec((int)((r-l)*artBounds.width()),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec((int)((bot-t)*artBounds.height()),MeasureSpec.EXACTLY));}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){position(play,.19f,.745f);position(settings,.28f,.872f);}
    private void position(Button b,float x,float y){int l=(int)(artBounds.left+x*artBounds.width()),t=(int)(artBounds.top+y*artBounds.height());b.layout(l,t,l+b.getMeasuredWidth(),t+b.getMeasuredHeight());}
    @Override protected void onDraw(Canvas c){
                float density=getResources().getDisplayMetrics().density;
        int sky=image.getPixel(image.getWidth()/2,1),grass=image.getPixel(image.getWidth()/2,image.getHeight()-2);
        paint.setShader(new LinearGradient(0,0,0,Math.max(1,artBounds.top),0xff078bc7,sky,Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),artBounds.top,paint);paint.setShader(null);
        paint.setShader(new LinearGradient(0,artBounds.bottom,0,getHeight(),grass,0xff123b20,Shader.TileMode.CLAMP));c.drawRect(0,artBounds.bottom,getWidth(),getHeight(),paint);paint.setShader(null);
        paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));paint.setTextSize(12*density);paint.setColor(0xffffedb8);
        if(artBounds.top>48*density)c.drawText("БЕСКОНЕЧНОЕ ПРИКЛЮЧЕНИЕ",getWidth()/2f,artBounds.top/2+4*density,paint);
        if(getHeight()-artBounds.bottom>48*density)c.drawText("Построй загон. Спрячь стадо.",getWidth()/2f,(artBounds.bottom+getHeight())/2+4*density,paint);
        paint.setColor(Color.WHITE);
        c.drawBitmap(image,null,artBounds,paint);
    }
}

