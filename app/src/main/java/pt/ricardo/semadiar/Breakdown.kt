package pt.ricardo.semadiar

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import java.text.Normalizer

/** Ajuda a partir uma tarefa em passos pequenos, sem IA e com IA (ida e volta). */
object Breakdown {

    private class Template(val keys: List<String>, val steps: List<String>)

    // As chaves estão sem acentos e em minúsculas
    private val templates = listOf(
        Template(
            listOf("consulta", "medico", "dentista", "marcar", "exame", "analises"),
            listOf("Encontrar o número ou o site para marcar", "Ver na agenda dois dias que me dão jeito", "Ligar ou marcar online", "Pôr a marcação no calendário")
        ),
        Template(
            listOf("ligar", "telefonar", "telefone", "chamada"),
            listOf("Encontrar o número", "Escrever em duas linhas o que quero dizer", "Ligar (se não atenderem, fica feito na mesma por hoje)")
        ),
        Template(
            listOf("email", "e-mail", "mail", "responder", "mensagem"),
            listOf("Abrir o email ou a conversa", "Escrever só a primeira frase", "Acabar e enviar, mesmo que não fique perfeito")
        ),
        Template(
            listOf("irs", "formulario", "papelada", "papeis", "documento", "candidatura", "seguro", "financas", "seguranca social", "renovar", "cartao de cidadao"),
            listOf("Juntar os papéis todos numa pasta ou num sítio", "Ler só o que é pedido, sem preencher nada", "Preencher a primeira parte", "Ver o que falta e anotar", "Rever e entregar")
        ),
        Template(
            listOf("pagar", "conta", "factura", "fatura", "multa", "transferencia"),
            listOf("Encontrar a factura ou a referência", "Abrir a app do banco", "Pagar e guardar o comprovativo")
        ),
        Template(
            listOf("arrumar", "limpar", "organizar", "lavar", "roupa", "loica", "casa", "quarto", "gaveta", "armario"),
            listOf("Escolher só uma zona pequena (uma gaveta, uma mesa)", "Pôr num saco tudo o que é lixo", "Arrumar o resto durante 10 minutos", "Parar e ver como ficou")
        ),
        Template(
            listOf("comprar", "compras", "encomendar", "supermercado"),
            listOf("Escrever a lista do que falta", "Escolher onde comprar", "Comprar (ou encomendar online)")
        ),
        Template(
            listOf("escrever", "relatorio", "trabalho", "texto", "apresentacao", "artigo", "tese", "projecto", "projeto"),
            listOf("Abrir o ficheiro e escrever o título", "Pôr as ideias soltas em tópicos, sem frases", "Escrever o primeiro parágrafo, mesmo mal escrito", "Continuar tópico a tópico", "Rever no fim")
        ),
        Template(
            listOf("estudar", "exame", "teste", "ler", "livro"),
            listOf("Separar o material numa pilha", "Escolher só um capítulo ou tema", "Ler 10 minutos e sublinhar", "Escrever três ideias principais")
        ),
        Template(
            listOf("devolver", "levar", "entregar", "correios", "enviar"),
            listOf("Pôr a coisa junto à porta ou na mala", "Ver o horário do sítio", "Ir lá e entregar")
        )
    )

    val generic = listOf(
        "Escrever numa frase o que é ter isto feito",
        "Juntar o que preciso (papéis, um link, um número)",
        "Fazer só a parte mais fácil durante 10 minutos"
    )

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /** Passos sugeridos para esta tarefa, sem IA. */
    fun suggest(title: String): List<String> {
        val t = " " + normalize(title).replace(Regex("[^a-z0-9 -]"), " ") + " "
        return templates.firstOrNull { tp -> tp.keys.any { k -> t.contains(" $k") } }?.steps ?: generic
    }

    fun prompt(t: Task): String = buildString {
        append("Ajuda-me a começar uma tarefa que ando a adiar. ")
        append("Divide-a em 3 a 6 passos muito pequenos e concretos, cada um com 15 minutos no máximo. ")
        append("O primeiro passo tem de ser tão fácil que se faça em 2 minutos. ")
        append("Responde só com a lista numerada, um passo por linha, sem introdução nem explicações.\n\n")
        append("Tarefa: ").append(t.title)
        if (t.steps.isNotEmpty()) {
            append("\nPassos que já tenho: ").append(t.steps.joinToString("; ") { it.text })
        }
    }

    /** Copia o pedido e abre a escolha de app (Gemini, ChatGPT, Claude...). */
    fun askAi(c: Context, t: Task) {
        val text = prompt(t)
        copy(c, text)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(send, "Pedir à IA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(c: Context, text: String) {
        val cm = c.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Sem Adiar", text))
    }

    fun clipboard(c: Context): String? {
        val cm = c.getSystemService(ClipboardManager::class.java)
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(c)?.toString()
    }

    private val bullet = Regex("^\\s*(?:[-*•–·]|\\d{1,2}\\s*[.)\\-:]|passo\\s*\\d+\\s*[.):\\-]?)\\s*", RegexOption.IGNORE_CASE)

    /** Transforma a resposta da IA numa lista de passos. */
    fun parse(text: String): List<String> =
        text.lines()
            .map { it.replace("**", "").replace("__", "").trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.endsWith(":") && !bullet.containsMatchIn(it) }   // "Aqui estão os passos:"
            .map { it.replace(bullet, "").trim().trimEnd('.') }
            .filter { it.length in 2..200 }
            .filterNot { it.startsWith("Tarefa:") || it.startsWith("Ajuda-me a começar") }
            .take(12)

    /** Mensagem para pedir ajuda a uma pessoa. */
    fun askPerson(c: Context, t: Task) {
        val text = "Olá! Ando a adiar isto há uns dias e dava-me jeito uma ajuda: \"${t.title}\". Podes ajudar-me?"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        c.startActivity(Intent.createChooser(send, "Pedir ajuda a alguém").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
