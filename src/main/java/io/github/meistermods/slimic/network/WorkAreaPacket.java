package io.github.meistermods.slimic.network;

import io.github.meistermods.slimic.entity.SlimeWorkArea;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record WorkAreaPacket(
    ResourceLocation dimension, int entityId, UUID companion, SlimeWorkArea area) {
  public void encode(FriendlyByteBuf buffer) {
    buffer.writeResourceLocation(dimension);
    buffer.writeVarInt(entityId);
    buffer.writeUUID(companion);
    buffer.writeBoolean(area.planned());
    buffer.writeByte(area.shapes().size());
    for (var shape : area.shapes()) {
      var box = shape.bounds();
      buffer.writeDouble(box.minX);
      buffer.writeDouble(box.minY);
      buffer.writeDouble(box.minZ);
      buffer.writeDouble(box.maxX);
      buffer.writeDouble(box.maxY);
      buffer.writeDouble(box.maxZ);
      buffer.writeBoolean(shape.radial());
      buffer.writeBoolean(shape.next());
    }
  }

  public static WorkAreaPacket decode(FriendlyByteBuf buffer) {
    var dimension = buffer.readResourceLocation();
    int id = buffer.readVarInt();
    var companion = buffer.readUUID();
    boolean planned = buffer.readBoolean();
    int count = buffer.readUnsignedByte();
    if (count > SlimeWorkArea.MAX_SHAPES)
      throw new IllegalArgumentException("Oversized work-area preview");
    var shapes = new ArrayList<SlimeWorkArea.Shape>();
    for (int i = 0; i < count; i++) {
      var bounds =
          new AABB(
              buffer.readDouble(),
              buffer.readDouble(),
              buffer.readDouble(),
              buffer.readDouble(),
              buffer.readDouble(),
              buffer.readDouble());
      shapes.add(new SlimeWorkArea.Shape(bounds, buffer.readBoolean(), buffer.readBoolean()));
    }
    return new WorkAreaPacket(dimension, id, companion, new SlimeWorkArea(planned, shapes));
  }

  public static void handle(WorkAreaPacket packet, Supplier<NetworkEvent.Context> supplier) {
    var context = supplier.get();
    context.enqueueWork(
        () ->
            DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> io.github.meistermods.slimic.client.WorkAreaPreview.receive(packet)));
    context.setPacketHandled(true);
  }
}
