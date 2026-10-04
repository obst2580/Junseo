package com.junseo.city.economy;

import com.junseo.city.JunseoCity;
import com.junseo.city.logic.Money;
import com.junseo.city.logic.PlayerData;
import com.junseo.city.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** /돈, /송금, /은행 */
public final class EconomyCommands implements TabExecutor {
    private final JunseoCity plugin;

    public EconomyCommands(JunseoCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("게임 안에서만 쓸 수 있어요.");
            return true;
        }
        switch (command.getName()) {
            case "money" -> money(player);
            case "pay" -> pay(player, args);
            case "bank" -> bank(player, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void money(Player player) {
        PlayerData data = plugin.data().get(player);
        Text.send(player, "현금 <green>" + plugin.settings().money(data.cash()) + "</green>  ·  은행 <aqua>"
                + plugin.settings().money(data.bank()) + "</aqua>");
    }

    private void pay(Player player, String[] args) {
        if (args.length < 2) {
            Text.send(player, "<yellow>사용법: /송금 <이름> <금액>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            Text.send(player, "<red>" + Text.esc(args[0]) + " 님은 지금 접속해 있지 않아요.");
            return;
        }
        if (target.equals(player)) {
            Text.send(player, "<red>자기 자신에게는 보낼 수 없어요.");
            return;
        }
        long amount = Money.parse(args[1]);
        if (amount <= 0) {
            Text.send(player, "<red>금액을 숫자로 적어 주세요. (예: 1000, 1만)");
            return;
        }
        PlayerData from = plugin.data().get(player);
        if (!from.takeBank(amount)) {
            Text.send(player, "<red>은행 잔액이 부족해요. (은행 " + plugin.settings().money(from.bank()) + ")");
            return;
        }
        plugin.data().get(target).addBank(amount);
        String money = plugin.settings().money(amount);
        Text.send(player, "<green>" + target.getName() + " 님에게 " + money + "을 송금했어요.");
        Text.send(target, "<green>" + player.getName() + " 님이 " + money + "을 보냈어요! (은행 입금)");
    }

    private void bank(Player player, String[] args) {
        if (args.length == 0) {
            if (!plugin.economy().canUseBank(player)) {
                Text.send(player, "<red>ATM이나 은행원 근처에서만 쓸 수 있어요.");
                return;
            }
            plugin.economy().showBankMenu(player);
            return;
        }
        if (args.length < 2) {
            Text.send(player, "<yellow>사용법: /은행 <입금|출금> <금액|전부>");
            return;
        }
        if (!plugin.economy().canUseBank(player)) {
            Text.send(player, "<red>ATM이나 은행원 근처에서만 쓸 수 있어요.");
            return;
        }
        PlayerData data = plugin.data().get(player);
        String action = args[0].toLowerCase(Locale.ROOT);
        boolean deposit = action.equals("deposit") || action.equals("입금");
        boolean withdraw = action.equals("withdraw") || action.equals("출금");
        if (!deposit && !withdraw) {
            Text.send(player, "<yellow>사용법: /은행 <입금|출금> <금액|전부>");
            return;
        }
        boolean all = args[1].equalsIgnoreCase("all") || args[1].equals("전부");
        long amount = all ? (deposit ? data.cash() : data.bank()) : Money.parse(args[1]);
        if (amount <= 0) {
            Text.send(player, "<red>" + (all ? "넣거나 뺄 돈이 없어요." : "금액을 숫자로 적어 주세요."));
            return;
        }
        boolean ok = deposit ? data.deposit(amount) : data.withdraw(amount);
        if (!ok) {
            Text.send(player, "<red>" + (deposit ? "현금" : "은행 잔액") + "이 부족해요.");
            return;
        }
        Text.send(player, "<green>" + plugin.settings().money(amount) + (deposit ? " 입금" : " 출금") + " 완료! "
                + "<gray>(현금 " + plugin.settings().money(data.cash()) + " · 은행 " + plugin.settings().money(data.bank()) + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("bank")) {
            if (args.length == 1) {
                return List.of("입금", "출금");
            }
            if (args.length == 2) {
                return List.of("1000", "10000", "전부");
            }
        }
        if (command.getName().equals("pay") && args.length == 1) {
            return null; // 접속자 이름 자동완성
        }
        return List.of();
    }
}
