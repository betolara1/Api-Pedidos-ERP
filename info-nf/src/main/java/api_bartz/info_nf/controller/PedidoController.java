package api_bartz.info_nf.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import api_bartz.info_nf.dto.PedidoDTO;
import api_bartz.info_nf.service.PedidoService;

@RestController 
@RequestMapping("/pedido")
public class PedidoController {
    private final PedidoService pedidoService;

    private PedidoController (PedidoService pedidoService){
        this.pedidoService = pedidoService;
    }

    @GetMapping 
    public ResponseEntity<List<PedidoDTO>> listAll(){
        List<PedidoDTO> listaPedido = pedidoService.listarTodosPedidos();

        if(listaPedido.isEmpty()) return ResponseEntity.noContent().build();

        return ResponseEntity.ok(listaPedido);
    }


    // URL: /pedido/search?pedido=70155
    @GetMapping (value = "/search", params = "pedido")
    public ResponseEntity <List<PedidoDTO>> procurarPedido(@RequestParam ("pedido") String query){
        List<PedidoDTO> resultado = pedidoService.procurarPedido(query);

        if(resultado.isEmpty()) return ResponseEntity.noContent().build();

        return ResponseEntity.ok(resultado);
    }
}
