package cn.itcast.demo.mymmorpg.service;

import cn.itcast.demo.mymmorpg.client.BagCommandClient;
import cn.itcast.demo.mymmorpg.protocol.MessageId;
import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "game.bag.remote.enabled", havingValue = "true")
public class RemoteBagGateway implements RemoteBagClient {

    private final BagCommandClient bagCommandClient;

    public RemoteBagGateway(BagCommandClient bagCommandClient) {
        this.bagCommandClient = bagCommandClient;
    }

    @Override
    public ProtocolMessage handleGetBagInfo(long playerId, GetBagInfoCsReq req) {
        return new ProtocolMessage(MessageId.GET_BAG_INFO_SC_RSP, bagCommandClient.info(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleUseItem(long playerId, UseItemCsReq req) {
        return new ProtocolMessage(MessageId.USE_ITEM_SC_RSP, bagCommandClient.use(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleDiscardItem(long playerId, DiscardItemCsReq req) {
        return new ProtocolMessage(MessageId.DISCARD_ITEM_SC_RSP, bagCommandClient.discard(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleSortBag(long playerId, SortBagCsReq req) {
        return new ProtocolMessage(MessageId.SORT_BAG_SC_RSP, bagCommandClient.sort(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleSellItem(long playerId, SellItemCsReq req) {
        return new ProtocolMessage(MessageId.SELL_ITEM_SC_RSP, bagCommandClient.sell(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleEquipItem(long playerId, EquipItemCsReq req) {
        return new ProtocolMessage(MessageId.EQUIP_ITEM_SC_RSP, bagCommandClient.equip(playerId, req.toByteArray()));
    }

    @Override
    public ProtocolMessage handleUnequipItem(long playerId, UnequipItemCsReq req) {
        return new ProtocolMessage(MessageId.UNEQUIP_ITEM_SC_RSP, bagCommandClient.unequip(playerId, req.toByteArray()));
    }
}
