package cn.itcast.demo.mymmorpg.web;

import cn.itcast.demo.mymmorpg.protocol.ProtocolMessage;
import cn.itcast.demo.mymmorpg.protocol.protobuf.SendChatMsgCsReq;
import cn.itcast.demo.mymmorpg.service.ChatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "spring.application.name", havingValue = "chat-service")
@RequestMapping("/internal/chat")
public class InternalChatController {

    private final ChatService chatService;

    public InternalChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/send", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> send(@RequestHeader("X-Player-Id") long playerId, @RequestBody byte[] body) throws Exception {
        ProtocolMessage msg = chatService.handleSendChat(playerId, SendChatMsgCsReq.parseFrom(body));
        return ResponseEntity.ok(msg.payload());
    }
}
