package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A chunky wood/leaf/parchment system based on the supplied gameplay UI reference. */
final class MaterialSurface extends Drawable {
    static final int PANEL=0,WOOD=1,PRIMARY=2,PAPER=3,SLOT=4;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r=new RectF();
    private final int kind;
    private final float d;
    private final BitmapShader wood;
    MaterialSurface(Context context,int kind,int theme){
        this.kind=kind;d=context.getResources().getDisplayMetrics().density;
        Bitmap texture=ThemeArt.bitmap(context,R.drawable.wood_grain);wood=new BitmapShader(texture,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);
        Matrix m=new Matrix();m.setScale(300*d/texture.getWidth(),300*d/texture.getHeight());wood.setLocalMatrix(m);
    }
    @Override public void draw(Canvas c){
        r.set(getBounds());r.inset(2*d,2*d);r.bottom-=4*d;
        float radius=d*(kind==PANEL||kind==PAPER?22:12);
        p.setStyle(Paint.Style.FILL);p.setColor(0x650b210c);c.drawRoundRect(r.left,r.top+6*d,r.right,r.bottom+6*d,radius,radius,p);
        int top=kind==PRIMARY?0xffa7d72e:kind==PAPER?0xffffe7a6:kind==SLOT?0xff86511f:0xffbe813e;
        int bottom=kind==PRIMARY?0xff4a8a04:kind==PAPER?0xffedc573:kind==SLOT?0xff513011:0xff744217;
        p.setColor(Color.WHITE);p.setShader(new LinearGradient(0,r.top,0,r.bottom,top,bottom,Shader.TileMode.CLAMP));c.drawRoundRect(r,radius,radius,p);p.setShader(null);
        c.save();Path clip=new Path();clip.addRoundRect(r,radius,radius,Path.Direction.CW);c.clipPath(clip);
        if(kind!=PRIMARY && kind!=PAPER){p.setShader(wood);p.setAlpha(kind==SLOT?45:105);c.drawRect(r,p);p.setShader(null);p.setAlpha(255);}
        p.setColor(kind==PRIMARY?0x40eeffbc:0x25fff1bf);c.drawRoundRect(r.left+4*d,r.top+3*d,r.right-4*d,r.top+Math.min(r.height()*.3f,18*d),radius,radius,p);c.restore();
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f*d);p.setColor(kind==PRIMARY?0xff315207:0xff48270e);c.drawRoundRect(r,radius,radius,p);
        r.inset(3*d,3*d);p.setStrokeWidth(1.4f*d);p.setColor(kind==PRIMARY?0xffd1ec77:0xffdda45b);c.drawRoundRect(r,radius-2*d,radius-2*d,p);
        if(kind==PANEL||kind==PAPER){r.inset(4*d,4*d);p.setColor(0x50804c22);c.drawRoundRect(r,radius-5*d,radius-5*d,p);}
        p.setStyle(Paint.Style.FILL);
        if(kind==WOOD){p.setColor(0xff4f391e);c.drawCircle(r.left+6*d,r.centerY(),2*d,p);c.drawCircle(r.right-6*d,r.centerY(),2*d,p);p.setColor(0xffe3bb76);c.drawCircle(r.left+5.5f*d,r.centerY()-.5f*d,.9f*d,p);c.drawCircle(r.right-6.5f*d,r.centerY()-.5f*d,.9f*d,p);}
    }
    @Override public void setAlpha(int alpha){} @Override public void setColorFilter(ColorFilter filter){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
