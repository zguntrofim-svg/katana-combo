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
import org.joml.Matrix3f;
import java.util.*;

/** Single weapon rig, two explicit grip sockets, and two-bone arms driven from those sockets. */
public final class RaizenModel extends EntityModel<RaizenBoss> {
    private record Cube(float[] box,int[] uv,String material){}
    private record Node(String name,float[] pivot,float[] rotation,Cube[] cubes,Node[] children){}
    private record File(int textureWidth,int textureHeight,Node[] nodes){}
    private final ModelPart root=load("raizen"),weapon=load("amakiri");
    private final ModelPart torso=root.getChild("torso"),head=root.getChild("head");
    public static final Vector3f REAR_SOCKET=new Vector3f(0,RaizenMotion.REAR_GRIP,0),FRONT_SOCKET=new Vector3f(0,RaizenMotion.FRONT_GRIP,0);
    private final Map<String,Bone> bones=new LinkedHashMap<>();
    private final Bone weaponBone;
    private final Map<String,Vector3f> shoulderAnchors=new HashMap<>();
    private final Quaternionf shoulderFrame=new Quaternionf();
    public RaizenModel(){
        for(String name:new String[]{"right_upper","right_lower","right_hand","left_upper","left_lower","left_hand"})bones.put(name,new Bone(root.getChild(name)));
        weaponBone=new Bone(weapon);
    }
    private static final class Bone {
        final ModelPart part;Vector3f position=new Vector3f();Quaternionf rotation=new Quaternionf();float stretch=1;
        Bone(ModelPart part){
            this.part=part;part.pivotX=part.pivotY=part.pivotZ=0;part.pitch=part.yaw=part.roll=0;part.xScale=part.yScale=part.zScale=1;
        }
        void pose(Vector3f p,Quaternionf q,float scale){position.set(p);rotation.set(q).normalize();stretch=scale;}
        void push(MatrixStack matrices){matrices.push();matrices.translate(position.x/16,position.y/16,position.z/16);matrices.multiply(rotation);matrices.scale(1,stretch,1);}
        void render(MatrixStack matrices,VertexConsumer out,int light,int overlay,int color){push(matrices);part.render(matrices,out,light,overlay,color);matrices.pop();}
    }
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
        shoulderFrame.set(body);
        Vector3f neck=new Vector3f(0,-21,0).rotate(body).add(0,torso.pivotY,0);
        head.pivotX=neck.x;head.pivotY=neck.y;head.pivotZ=neck.z;
        head.yaw=headYaw*(float)Math.PI/180*.55f+torso.yaw*.2f;
        head.pitch=headPitch*(float)Math.PI/180*.35f+RaizenMotion.rad(v[10]);
        Vector3f axis=pose.axis().rotate(body);
        Vector3f center=pose.center().rotate(body).add(0,torso.pivotY,0);
        Quaternionf weaponRotation=RaizenMotion.weaponRotation(type,age,pose,body);
        Vector3f base=new Vector3f(center).fma(-RaizenMotion.GRIP_CENTER,axis);
        weaponBone.pose(base,weaponRotation,1);
        Vector3f rear=new Vector3f(REAR_SOCKET).rotate(weaponRotation).add(base);
        Vector3f front=new Vector3f(FRONT_SOCKET).rotate(weaponRotation).add(base);
        arm("right",new Vector3f(-10,-15,0).rotate(body).add(0,torso.pivotY,0),rear,weaponRotation,body,-1);
        arm("left",new Vector3f(10,-15,0).rotate(body).add(0,torso.pivotY,0),front,weaponRotation,body,1);
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
    private void arm(String side,Vector3f shoulder,Vector3f grip,Quaternionf weaponRotation,Quaternionf body,float sign){
        shoulderAnchors.put(side,new Vector3f(shoulder));
        Vector3f shaft=new Vector3f(0,1,0).rotate(weaponRotation);
        Vector3f back=new Vector3f(shoulder).sub(grip);back.fma(-back.dot(shaft),shaft);
        if(back.lengthSquared()<.0001f){back=new Vector3f(0,0,1).rotate(body);back.fma(-back.dot(shaft),shaft);}
        Vector3f wrist=new Vector3f(grip).fma(2.2f,back.normalize());
        Vector3f direction=new Vector3f(wrist).sub(shoulder);float distance=Math.max(.001f,direction.length());direction.div(distance);
        float upper=13.5f,lower=12.5f;
        float along=(distance*distance+upper*upper-lower*lower)/(2*distance);along=Math.max(0,Math.min(upper,along));
        Vector3f pole=new Vector3f(sign,.55f,-.4f).rotate(body);pole.fma(-pole.dot(direction),direction);
        if(pole.lengthSquared()<.0001f){pole=new Vector3f(sign,0,0).rotate(body);pole.fma(-pole.dot(direction),direction);}
        pole.normalize();
        Vector3f elbow=new Vector3f(shoulder).fma(along,direction).fma((float)Math.sqrt(Math.max(0,upper*upper-along*along)),pole);
        Vector3f normal=new Vector3f(direction).cross(pole).normalize();
        segment(bones.get(side+"_upper"),shoulder,elbow,12,normal);
        segment(bones.get(side+"_lower"),elbow,wrist,11,normal);
        bones.get(side+"_hand").pose(grip,weaponRotation,1);
    }
    private static void segment(Bone bone,Vector3f from,Vector3f to,float nominal,Vector3f planeNormal){
        Vector3f y=new Vector3f(to).sub(from);float length=Math.max(.001f,y.length());y.div(length);
        Vector3f x=new Vector3f(planeNormal).fma(-planeNormal.dot(y),y).normalize();
        Vector3f z=new Vector3f(x).cross(y).normalize();
        Quaternionf q=new Quaternionf().setFromNormalized(new Matrix3f().setColumn(0,x).setColumn(1,y).setColumn(2,z));
        bone.pose(from,q,length/nominal);
    }
    @Override public void render(MatrixStack matrices,VertexConsumer vertices,int light,int overlay,int color){
        for(Bone bone:bones.values())bone.part.visible=false;
        try{root.render(matrices,vertices,light,overlay,color);}finally{for(Bone bone:bones.values())bone.part.visible=true;}
        for(String side:new String[]{"right","left"})for(int i=0;i<3;i++)root.getChild(side+"_upper").getChild(side+"_shoulder_"+i).visible=false;
        try{for(Bone bone:bones.values())bone.render(matrices,vertices,light,overlay,color);}
        finally{for(String side:new String[]{"right","left"})for(int i=0;i<3;i++)root.getChild(side+"_upper").getChild(side+"_shoulder_"+i).visible=true;}
        // Heavy pauldrons follow the torso/shoulder socket, not the forearm's twist.
        for(String side:new String[]{"right","left"}){
            Vector3f p=shoulderAnchors.get(side);if(p==null)continue;
            matrices.push();matrices.translate(p.x/16,p.y/16,p.z/16);matrices.multiply(shoulderFrame);
            for(int i=0;i<3;i++)root.getChild(side+"_upper").getChild(side+"_shoulder_"+i).render(matrices,vertices,light,overlay,color);
            matrices.pop();
        }
    }
    public void weapon(MatrixStack matrices,VertexConsumer vertices,int light){weaponBone.render(matrices,vertices,light,OverlayTexture.DEFAULT_UV,-1);}
    public void glow(MatrixStack matrices,VertexConsumer vertices){
        matrices.push();head.rotate(matrices);head.getChild("eyes").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
        matrices.push();torso.rotate(matrices);torso.getChild("energy").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
    }
    public void weaponGlow(MatrixStack matrices,VertexConsumer vertices){
        weaponBone.push(matrices);weapon.getChild("energy").render(matrices,vertices,15728880,OverlayTexture.DEFAULT_UV);matrices.pop();
    }
}
