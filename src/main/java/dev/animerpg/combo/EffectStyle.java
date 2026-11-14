package dev.animerpg.combo;
public enum EffectStyle {
    MOON("Moon Arc","Лунный серп · лепестки",0xa62dff,0xffefff,1.65f,6.8f),
    CRIMSON("Crimson Cross","Алый перекрёстный след",0xff183e,0xffedef,1.05f,5.0f),
    VOID("Void Ripple","Тёмная волна · осколки",0x7c1ddb,0xe9baff,1.7f,6.8f),
    AZURE("Azure Flash","Тонкий лазурный росчерк",0x00ceff,0xeeffff,0.85f,3.8f),
    PHANTOM("Phantom Echo","Фиолетовые послеследы",0xb445ff,0xfbedff,1.1f,6.0f),
    STORM("Storm Fang","Белая кромка · молнии",0x338bff,0xf4ffff,1.35f,5.2f);
    public final String title,description;public final int color,edge;public final float width,lifetime;
    EffectStyle(String title,String description,int color,int edge,float width,float lifetime){this.title=title;this.description=description;this.color=color;this.edge=edge;this.width=width;this.lifetime=lifetime;}
    public static EffectStyle get(int id){return id>=0 && id<values().length?values()[id]:AZURE;}
}
