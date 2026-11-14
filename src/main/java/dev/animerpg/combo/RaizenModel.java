package dev.animerpg.combo;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Single weapon rig, two explicit grip sockets, and two-bone arms driven from those sockets. */
public final class RaizenModel extends EntityModel<RaizenBoss> {
    private record Cube(float[] box,int[] uv,String material){}
    private record Node(String name,float[] pivot,float[] rotation,Cube[] cubes,Node[] children){}
    private record File(int textureWidth,int textureHeight,Node[] nodes){}
    private final ModelPart root=load("raizen"),weapon=load("amakiri");
    private final ModelPart torso=root.getChild("torso"),head=root.getChild("head");
    public static final Vector3f REAR_SOCKET=new Vector3f(0,10,0),FRONT_SOCKET=new Vector3f(0,25,0);
    private static ModelPart load(String name){
        try(var stream=RaizenModel.class.getResourceAsStream("/assets/katana_combo/models/entity/"+name+".json")){
            if(stream==null)throw new IllegalStateException("Missing boss model "+name);
            File file=new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),File.class);
            ModelData data=new ModelData();for(Node node:file.nodes)add(data.getRoot(),node);
            return TexturedModelData.of(data,file.textureWidth,file.textureHeight).createModel();
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void add(ModelPartData parent,Node node){
        ModelPartBuilder builder=ModelPartBuilder.create();
        for(Cube c:node.cubes){float[] b=c.box;builder.uv(c.uv[0],c.uv[1]).cuboid(b[0],b[1],b[2],b[3],b[4],b[5]);}
        float[] p=node.pivot,r=node.rotation;
        var part=parent.addChild(node.name,builder,ModelTransform.of(p[0],p[1],p[2],r[0],r[1],r[2]));
        for(Node child:node.children)add(part,child);
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    @Override public void setAngles(RaizenBoss entity,float limb,float distance,float progress,float headYaw,float headPitch){
        float delta=Math.max(0,Math.min(1,progress-entity.age));float age=entity.attackAge(delta);
        int type=entity.action();
        RaizenMotion.Pose pose=RaizenMotion.sample(type,age);float[] v=pose.v();
        float moving=Math.min(1,distance*2),walk=(float)Math.sin(limb*.65f);
        float breath=(float)Math.sin(progress*6.283185f/60)*.16f;
        torso.yaw=RaizenMotion.rad(v[5]);torso.pitch=RaizenMotion.rad(v[6]);torso.roll=RaizenMotion.rad(v[7])+(type==0?walk*moving*.025f:0);
        torso.pivotY=v[8]+breath+(type==0?Math.abs(walk)*moving*.40f:0);
        Quaternionf body=new Quaternionf().rotationZYX(torso.roll,torso.yaw,torso.pitch);
        Vector3f neck=new Vector3f(0,-21,0).rotate(body).add(0,torso.pivotY,0);
        head.pivotX=neck.x;head.pivotY=neck.y;head.pivotZ=neck.z;
        head.yaw=headYaw*(float)Math.PI/180*.55f+torso.yaw*.2f;
        head.pitch=headPitch*(float)Math.PI/180*.35f+RaizenMotion.rad(v[10]);
        Vector3f axis=pose.axis().rotate(body);
        Vector3f center=pose.center().rotate(body).add(0,torso.pivotY,0);
        Quaternionf weaponRotation=RaizenMotion.weaponRotation(type,age,axis,body);
        Vector3f base=new Vector3f(center).fma(-17.5f,axis);
        weapon.pivotX=base.x;weapon.pivotY=base.y;weapon.pivotZ=base.z;rotate(weapon,weaponRotation);
        Vector3f rear=new Vector3f(REAR_SOCKET).rotate(weaponRotation).add(base);
        Vector3f front=new Vector3f(FRONT_SOCKET).rotate(weaponRotation).add(base);
        arm("right",new Vector3f(-10,-15,0).rotate(body).add(0,torso.pivotY,0),rear,weaponRotation,-1);
        arm("left",new Vector3f(10,-15,0).rotate(body).add(0,torso.pivotY,0),front,weaponRotation,1);
        for(int i=0;i<2;i++){
            var leg=root.getChild(i==0?"right_leg":"left_leg");
            float step=(float)Math.sin(limb*.65f+(i==0?0:Math.PI));
            leg.roll=(i==0?1:-1)*v[9];
            leg.pitch=-.14f+(type==0?step*moving*.38f:type==RaizenMotion.DASH && age>5 && age<17?step*.30f:0);
            leg.getChild("shin").pitch=.28f+Math.max(0,step)*moving*.20f+v[8]*.06f;
        }
        var cape=root.getChild("cape");cape.yaw=torso.yaw*.4f;cape.pivotY=-17+torso.pivotY;
        for(int i=0;i<9;i++)cape.getChild("cape_strip_"+i).pitch=.10f+(float)Math.sin(progress*.09f+i*.55f)*.055f+Math.min(.25f,distance*.18f)+(type==RaizenMotion.DASH?.28f:0)+(type==RaizenMotion.TRANSITION?(float)Math.sin(progress*.45f+i)*.08f:0);
        weapon.getChild("tassel").roll=(float)Math.sin(progress*.12f)*.10f;
    }
    private void arm(String side,Vector3f shoulder,Vector3f grip,Quaternionf weaponRotation,float sign){
        Vector3f wrist=new Vector3f(0,0,2.2f).rotate(weaponRotation).add(grip);
        Vector3f direction=new Vector3f(wrist).sub(shoulder);float d=direction.length();direction.div(Math.max(.001f,d));
        float upper=12,lower=11;
        float along=(d*d+upper*upper-lower*lower)/(2*Math.max(.001f,d));along=Math.max(0,Math.min(upper,along));
        Vector3f pole=new Vector3f(sign,.1f,.75f);pole.fma(-pole.dot(direction),direction);
        if(pole.lengthSquared()<.0001f)pole.set(0,0,1).fma(-direction.z,direction);
        pole.normalize();
        Vector3f elbow=new Vector3f(shoulder).fma(along,direction).fma((float)Math.sqrt(Math.max(0,upper*upper-along*along)),pole);
        segment(root.getChild(side+"_upper"),shoulder,elbow,upper);
        segment(root.getChild(side+"_lower"),elbow,wrist,lower);
        ModelPart hand=root.getChild(side+"_hand");hand.pivotX=grip.x;hand.pivotY=grip.y;hand.pivotZ=grip.z;rotate(hand,weaponRotation);
    }
    private static void segment(ModelPart part,Vector3f from,Vector3f to,float nominal){
        Vector3f d=new Vector3f(to).sub(from);float length=d.length();
        part.pivotX=from.x;part.pivotY=from.y;part.pivotZ=from.z;
        rotate(part,new Quaternionf().rotationTo(new Vector3f(0,1,0),d.div(Math.max(.001f,length))));part.yScale=length/nominal;
    }
    private static void rotate(ModelPart part,Quaternionf q){Vector3f e=q.getEulerAnglesZYX(new Vector3f());part.pitch=e.x;part.yaw=e.y;part.roll=e.z;}
    @Override public void render(MatrixStack matrices,VertexConsumer vertices,int light,int overlay,int color){root.render(matrices,vertices,light,overlay,color);}
    public void weapon(MatrixStack matrices,VertexConsumer vertices,int light){weapon.render(matrices,vertices,light,OverlayTexture.DEFAULT_UV);}
    public void glow(MatrixStack matrices,VertexConsumer vertices){
        matrices.push();head.rotate(matrices);head.getChild("eyes").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
        matrices.push();torso.rotate(matrices);torso.getChild("energy").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
    }
    public void weaponGlow(MatrixStack matrices,VertexConsumer vertices){
        matrices.push();weapon.rotate(matrices);weapon.getChild("energy").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
    }
}
