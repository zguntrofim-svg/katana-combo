package dev.animerpg.combo;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
public final class RaizenRenderer extends MobEntityRenderer<RaizenBoss,RaizenModel> {
    private static final Identifier SKIN=Identifier.of("katana_combo","textures/entity/raizen.png");
    private static final Identifier WEAPON=Identifier.of("katana_combo","textures/entity/amakiri.png");
    public RaizenRenderer(EntityRendererFactory.Context context){
        super(context,new RaizenModel(),1.0f);
        addFeature(new FeatureRenderer<RaizenBoss,RaizenModel>(this){
            public void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,RaizenBoss boss,float limb,float distance,float delta,float age,float yaw,float pitch){
                getContextModel().weapon(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(WEAPON)),light);
                getContextModel().glow(matrices,vertices.getBuffer(RenderLayer.getEyes(SKIN)));
                getContextModel().weaponGlow(matrices,vertices.getBuffer(RenderLayer.getEyes(WEAPON)));
                RaizenEffects.render(matrices,vertices,boss,delta);
            }
        });
    }
    @Override public Identifier getTexture(RaizenBoss entity){return SKIN;}
}
