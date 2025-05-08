function initWinratesMatrix(elem) {
    const dataUrl = elem.attr("data-url");
    $.getJSON(dataUrl).done(function(jsonData) {
        elem.empty();

        const table = $("<table></table>");
        table.addClass("table-responsive");
        elem.append(table);
        elem.addClass("winrates-container-ready");

        const thead = $("<thead></thead>");
        table.append(thead);
        const theadTr = $("<tr></tr>");
        thead.append(theadTr);
        theadTr.append($("<th></th>"));

        $.each(jsonData.data, function(index, item) {
            const leaderName = item.leader.name;
            const leaderArt = item.leader.art;
            const th = $("<th></th>").html("<img src='" + leaderArt + "' width='120' height='40' alt='" + leaderName + "'/>");
            theadTr.append(th);
        });

        const tbody = $("<tbody></tbody>");
        table.append(tbody);

        $.each(jsonData.data, function(index, item) {
            const leaderName = item.leader.name;
            const leaderArt = item.leader.art;
            const tr = $("<tr></tr>");
            tbody.append(tr);

            const tdFirst = $("<td></td>").html("<img src='" + leaderArt + "' width='120' height='40' alt='" + leaderName + "'/>");
            tr.append(tdFirst);

            $.each(item.opponents, function(opIndex, opItem) {
                const opponent = opItem.name;
                const wr = opItem.winrate;
                const matches = opItem.matches;
                const td = $("<td></td>");
                const entry = $("<div></div>");
                entry.addClass("entry");
                td.append(entry);
                if(opponent == leaderName) {
                    entry.addClass("mirror");
                    entry.html("Mirror");
                } else if(wr !== undefined && matches !== undefined) {
                    if(matches == 0) {
                        entry.addClass("na");
                        entry.html("N/A");
                    } else {
                        if(wr >=60) {
                            entry.addClass("win")
                        } else if(wr >= 45) {
                            entry.addClass("even");
                        } else {
                            entry.addClass("loss");
                        }
                        const matchesStr = matches <= 1 ? "match" : "matches";
                        entry.html("<div class='winrate'>" + wr + "%</div><div class='matches'>" + matches + " " + matchesStr + "</div>");
                    }
                } else {
                    entry.addClass("na");
                    entry.html("N/A");
                }
                tr.append(td);
            });
        });
    });
}

document.addEventListener('DOMContentLoaded', function() {
    $(".winrates-container").each(function() {
        initWinratesMatrix($(this));
    });
});
