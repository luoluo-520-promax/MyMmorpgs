package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.port.BagCommandPort;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.RetCode;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DiscardItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.DiscardItemScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.EquipItemScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.GetBagInfoScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SellItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SellItemScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SortBagCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SortBagScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UnequipItemScRsp;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UseItemCsReq;
import cn.itcast.demo.mymmorpg.protocol.protobuf.UseItemScRsp;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BagCommandGateway {

    private final ObjectProvider<BagCommandPort> localService;
    private final ObjectProvider<RemoteBagClient> remoteClient;
    private final boolean remoteEnabled;

    public BagCommandGateway(
            ObjectProvider<BagCommandPort> localService,
            ObjectProvider<RemoteBagClient> remoteClient,
            @Value("${game.bag.remote.enabled:false}") boolean remoteEnabled) {
        this.localService = localService;
        this.remoteClient = remoteClient;
        this.remoteEnabled = remoteEnabled;
    }

    public ProtocolMessage handleGetBagInfo(long playerId, GetBagInfoCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleGetBagInfo(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.GET_BAG_INFO_SC_RSP,
                    GetBagInfoScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleGetBagInfo(playerId, req);
    }

    public ProtocolMessage handleUseItem(long playerId, UseItemCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleUseItem(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.USE_ITEM_SC_RSP,
                    UseItemScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleUseItem(playerId, req);
    }

    public ProtocolMessage handleDiscardItem(long playerId, DiscardItemCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleDiscardItem(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.DISCARD_ITEM_SC_RSP,
                    DiscardItemScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleDiscardItem(playerId, req);
    }

    public ProtocolMessage handleSortBag(long playerId, SortBagCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleSortBag(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.SORT_BAG_SC_RSP,
                    SortBagScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleSortBag(playerId, req);
    }

    public ProtocolMessage handleSellItem(long playerId, SellItemCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleSellItem(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.SELL_ITEM_SC_RSP,
                    SellItemScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleSellItem(playerId, req);
    }

    public ProtocolMessage handleEquipItem(long playerId, EquipItemCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleEquipItem(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.EQUIP_ITEM_SC_RSP,
                    EquipItemScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleEquipItem(playerId, req);
    }

    public ProtocolMessage handleUnequipItem(long playerId, UnequipItemCsReq req) {
        if (useRemote()) {
            return remoteClient.getObject().handleUnequipItem(playerId, req);
        }
        BagCommandPort local = localService.getIfAvailable();
        if (local == null) {
            return new ProtocolMessage(MessageId.UNEQUIP_ITEM_SC_RSP,
                    UnequipItemScRsp.newBuilder().setRetcode(RetCode.INTERNAL_ERROR).build().toByteArray());
        }
        return local.handleUnequipItem(playerId, req);
    }

    private boolean useRemote() {
        return remoteEnabled && remoteClient.getIfAvailable() != null;
    }
}
