package dev.animerpg.combo;

import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Arm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Two short skin segments per arm, joined at an elbow and attached to the actual rendered handle. */
public final class FirstPersonArms {
    private static final ModelPart[][][] MODELS=build();
    private static ModelPart[][][] build(){
        ModelPart[][][] models=new ModelPart[2][2][4];
        for(int slim=0;slim<2;slim++)for(int side=0;side<2;side++){
            int width=slim==1?3:4;
            for(int piece=0;piece<4;piece++){
                boolean sleeve=piece>=2;int segment=piece%2;
                int u=side==0?40:(sleeve?48:32);
                int v=side==0?(sleeve?32:16):48;
                ModelData data=new ModelData();
                data.getRoot().addChild("arm",ModelPartBuilder.create().uv(u,v+segment*6)
                    .cuboid(-width/2f,0,-2,width,6,4,new Dilation(sleeve?0.22f:0)),ModelTransform.NONE);
                models[slim][side][piece]=TexturedModelData.of(data,64,64).createModel().getChild("arm");
            }
        }
        return models;
    }
    public static void render(AbstractClientPlayerEntity player,Matrix4f root,Matrix4f item,
                              VertexConsumerProvider vertices,int light,float weight){
        if(player.isInvisible() || weight<=0.001f)return;
        Matrix4f handle=new Matrix4f(root).invert().mul(item);
        Vector3f main=handle.transformPosition(new Vector3f(-0.19f,-0.19f,0));
        Vector3f support=handle.transformPosition(new Vector3f(-0.265f,-0.265f,0));
        int mainSide=player.getMainArm()==Arm.RIGHT?0:1;
        drawArm(player,mainSide,main,root,vertices,light,weight);
        // Don't invent an extra hand when the offhand already holds an item.
        if(player.getOffHandStack().isEmpty())drawArm(player,1-mainSide,support,root,vertices,light,weight);
    }
    private static void drawArm(AbstractClientPlayerEntity player,int side,Vector3f wrist,Matrix4f root,
                                VertexConsumerProvider vertices,int light,float weight){
        float sign=side==0?1:-1;
        Vector3f shoulder=new Vector3f(sign*0.42f,-0.68f,-0.10f);
        Vector3f direction=new Vector3f(wrist).sub(shoulder);
        float distance=direction.length();if(distance<0.01f)return;
        direction.div(distance);
        // Limit arm stretch: the upper arm can leave the camera frustum rather than elongate the hand.
        if(distance>0.88f){shoulder.fma(distance-0.88f,direction);distance=0.88f;}
        float upper=0.47f,lower=0.43f;
        float along=(distance*distance+upper*upper-lower*lower)/(2*distance);
        along=Math.max(0,Math.min(upper,along));
        float bend=(float)Math.sqrt(Math.max(0,upper*upper-along*along));
        Vector3f pole=new Vector3f(sign*0.5f,-1,0.15f);
        pole.fma(-pole.dot(direction),direction);
        if(pole.lengthSquared()<0.0001f)pole.set(0,0,1).fma(-direction.z,direction);
        pole.normalize();
        Vector3f elbow=new Vector3f(shoulder).fma(along,direction).fma(bend,pole);
        int slim=player.getSkinTextures().model()==SkinTextures.Model.SLIM?1:0;
        var buffer=vertices.getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTextures().texture()));
        int color=((int)(255*Math.min(1,weight))<<24)|0xffffff;
        boolean sleeve=player.isPartVisible(side==0?PlayerModelPart.RIGHT_SLEEVE:PlayerModelPart.LEFT_SLEEVE);
        segment(MODELS[slim][side],0,shoulder,elbow,root,buffer,light,color,sleeve);
        segment(MODELS[slim][side],1,elbow,wrist,root,buffer,light,color,sleeve);
    }
    private static void segment(ModelPart[] parts,int index,Vector3f from,Vector3f to,Matrix4f root,
                                VertexConsumer buffer,int light,int color,boolean sleeve){
        Vector3f delta=new Vector3f(to).sub(from);float length=delta.length();if(length<0.001f)return;
        MatrixStack matrices=new MatrixStack();
        matrices.peek().getPositionMatrix().set(root);
        matrices.peek().getNormalMatrix().set(root).invert().transpose();
        matrices.translate(from.x,from.y,from.z);
        matrices.multiply(new Quaternionf().rotationTo(new Vector3f(0,1,0),delta.div(length)));
        matrices.scale(0.8f,length/0.375f,0.8f);
        parts[index].render(matrices,buffer,light,OverlayTexture.DEFAULT_UV,color);
        if(sleeve)parts[index+2].render(matrices,buffer,light,OverlayTexture.DEFAULT_UV,color);
    }
    private FirstPersonArms(){}
}
