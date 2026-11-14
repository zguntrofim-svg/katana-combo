package dev.animerpg.combo;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Boss-authentic UVs, player-sized rigid armor plates on the actual animated player bones. */
public final class StormArmorRenderer implements ArmorRenderer {
    private static final Identifier TEXTURE=Identifier.of("katana_combo","textures/entity/raizen.png");
    private final ModelPart base=RaizenModel.load("storm_armor"),glow=RaizenModel.load("storm_armor_glow");
    private static StormArmorRenderer INSTANCE;
    public static void init(){INSTANCE=new StormArmorRenderer();ArmorRenderer.register(INSTANCE,StormItems.ARMOR);}
    public static void firstPerson(LivingEntity player,int side,int segment,MatrixStack matrices,VertexConsumerProvider vertices,int light,int color){
        ItemStack armor=player.getEquippedStack(EquipmentSlot.CHEST);
        if(INSTANCE==null || !armor.isOf(StormItems.CHESTPLATE))return;
        String name=(side==0?"right":"left")+(segment==0?"_upper":"_lower");
        matrices.push();matrices.scale(segment==0?.94f:.86f,segment==0?.5f:.7f,segment==0?.94f:.86f);
        INSTANCE.base.getChild(name).render(matrices,ItemRenderer.getArmorGlintConsumer(vertices,RenderLayer.getArmorCutoutNoCull(TEXTURE),armor.hasGlint()),light,OverlayTexture.DEFAULT_UV,color);
        INSTANCE.glow.getChild(name).render(matrices,vertices.getBuffer(RenderLayer.getEyes(TEXTURE)),15728880,OverlayTexture.DEFAULT_UV,color);
        matrices.pop();
    }
    @Override public void render(MatrixStack matrices,VertexConsumerProvider vertices,ItemStack stack,LivingEntity entity,
                                 EquipmentSlot slot,int light,BipedEntityModel<LivingEntity> model){
        switch(slot){
            case HEAD->draw("helmet",model.head,0,-3.8f,0,.88f,.88f,.99f,matrices,vertices,stack,light);
            case CHEST->{
                draw("cuirass",model.body,0,11.5f,0,.58f,.64f,.56f,matrices,vertices,stack,light);
                for(int side=0;side<2;side++){
                    String name=side==0?"right":"left";float sign=side==0?-1:1;
                    ModelPart arm=side==0?model.rightArm:model.leftArm;
                    draw(name+"_upper",arm,sign,-2,0,.94f,.5f,.94f,matrices,vertices,stack,light);
                    draw(name+"_lower",arm,sign,4,0,.86f,.7f,.86f,matrices,vertices,stack,light);
                    // Heavy pads stay on the shoulder frame instead of rolling with the forearms.
                    for(int i=0;i<3;i++)draw(name+"_shoulder_"+i,model.body,sign*6,2,0,.65f,.65f,.65f,matrices,vertices,stack,light);
                }
                float time=entity.age+net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
                float speed=(float)Math.min(.30,entity.getVelocity().horizontalLength()*.9);
                for(int i=0;i<9;i++){
                    float pitch=.12f+speed+(entity.isInSneakingPose()?.2f:0)+(float)Math.sin(time*.09+i*.55)*.045f;
                    base.getChild("cape").getChild("cape_strip_"+i).pitch=pitch;
                    glow.getChild("cape").getChild("cape_strip_"+i).pitch=pitch;
                }
                draw("cape",model.body,0,1,3,.5f,.55f,.55f,matrices,vertices,stack,light);
            }
            case LEGS->{
                for(int side=0;side<2;side++){
                    String name=side==0?"right":"left";float sign=side==0?-1:1;
                    ModelPart leg=side==0?model.rightLeg:model.leftLeg;
                    draw(name+"_thigh",leg,0,0,0,.75f,.60f,.75f,matrices,vertices,stack,light);
                    draw(name+"_calf",leg,0,6,0,.72f,.62f,.72f,matrices,vertices,stack,light);
                    draw(name+"_skirt",leg,sign*.7f,0,0,.57f,.57f,.57f,matrices,vertices,stack,light);
                }
                draw("tabard",model.body,0,11.7f,-3.4f,.58f,.52f,.58f,matrices,vertices,stack,light);
            }
            case FEET->{
                draw("right_boot",model.rightLeg,0,6,0,.72f,.5f,.76f,matrices,vertices,stack,light);
                draw("left_boot",model.leftLeg,0,6,0,.72f,.5f,.76f,matrices,vertices,stack,light);
            }
            default->{}
        }
    }
    private void draw(String name,ModelPart bone,float x,float y,float z,float sx,float sy,float sz,MatrixStack matrices,
                      VertexConsumerProvider vertices,ItemStack stack,int light){
        matrices.push();bone.rotate(matrices);matrices.translate(x/16,y/16,z/16);matrices.scale(sx,sy,sz);
        base.getChild(name).render(matrices,ItemRenderer.getArmorGlintConsumer(vertices,RenderLayer.getArmorCutoutNoCull(TEXTURE),stack.hasGlint()),light,OverlayTexture.DEFAULT_UV,-1);
        glow.getChild(name).render(matrices,vertices.getBuffer(RenderLayer.getEyes(TEXTURE)),15728880,OverlayTexture.DEFAULT_UV,-1);
        matrices.pop();
    }
}
