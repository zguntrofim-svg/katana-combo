package dev.animerpg.combo;
public enum EffectStyle {
    MOON("Moon Arc","Лунный серп · лепестки",0x9139ef,0xffe8ff,1.3f,6.0f),
    CRIMSON("Crimson Cross","Алый перекрёстный след",0xd7193e,0xffe4e9,0.85f,4.4f),
    VOID("Void Ripple","Тёмная волна · осколки",0x58209c,0xdba0ff,1.45f,6.3f),
    AZURE("Azure Flash","Тонкий лазурный росчерк",0x16a8ed,0xeaffff,0.65f,3.1f),
    PHANTOM("Phantom Echo","Фиолетовые послеследы",0x8741cf,0xf7e7ff,0.85f,5.4f),
    STORM("Storm Fang","Белая кромка · молнии",0x3777ef,0xf3fbff,1.05f,4.5f);
    public final String title,description;public final int color,edge;public final float width,lifetime;
    EffectStyle(String title,String description,int color,int edge,float width,float lifetime){this.title=title;this.description=description;this.color=color;this.edge=edge;this.width=width;this.lifetime=lifetime;}
    public static EffectStyle get(int id){return id>=0 && id<values().length?values()[id]:AZURE;}
}
