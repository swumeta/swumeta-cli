function initWinratesMatrix(elem) {
    const embedded = "true" == elem.attr("data-embedded");
    const dataUrl = elem.attr("data-url");
    $.getJSON(dataUrl).done(function(jsonData) {
        elem.find(".spinner-border").remove();

        const table = $("<table></table>");
        table.addClass("table-responsive");
        elem.append(table);
        if(!embedded) {
            elem.addClass("winrates-matrix-ready");
        }

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
                        if(wr >= 55) {
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

    if(!embedded) {
        const container = elem[0];
        const button = elem.find(".go-fullscreen").get(0);
        let isHovering = false;

        function updatePosition() {
            if(isHovering) {
                const rect = container.getBoundingClientRect();
                button.style.top = (rect.top + 20) + 'px';
                button.style.left = (rect.right - button.offsetWidth - 30) + 'px';
            }
        }
        container.addEventListener('mouseenter', () => {
            isHovering = true;
            updatePosition();
            button.style.opacity = '1';
        });

        container.addEventListener('mouseleave', () => {
            isHovering = false;
            button.style.opacity = '0';
        });

        container.addEventListener('scroll', updatePosition);

        button.addEventListener('click', () => {
            const dlgBody = elem.find(".modal-body");
            dlgBody.empty();
            const dlgMatrix = $("<div></div>");
            dlgMatrix.addClass("winrates-matrix");
            dlgMatrix.addClass("winrates-matrix-fullscreen");
            dlgMatrix.attr("data-url", "winrates-matrix.json");
            dlgMatrix.attr("data-embedded", "true");
            initWinratesMatrix(dlgMatrix);
            dlgBody.append(dlgMatrix);

            const dlg = new bootstrap.Modal(elem.find(".modal").get(0), {});
            dlg.show();
        });
        window.addEventListener('scroll', updatePosition);
        window.addEventListener('resize', updatePosition);
    }
}

function calculateAxisRange(data, property, padding = 0.1) {
    const values = data.map(item => item[property]);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const range = max - min;

    return {
        min: Math.floor(min - range * padding),
        max: Math.ceil(max + range * padding)
    };
}

function getColorByMatches(matches) {
    const minMatches = 8;
    const maxMatches = 128;

    const normalized = Math.min(Math.max((matches - minMatches) / (maxMatches - minMatches), 0), 1);

    const r = Math.floor(224 - normalized * (224 - 176));
    const g = Math.floor(224 - normalized * (224 - 58));
    const b = Math.floor(224 - normalized * (224 - 46));

    return `rgb(${r},${g},${b})`;
}

function calculateSymbolSize(matches) {
    return 32;
}

function initWinratesChart(elem) {
    const dataUrl = elem.attr("data-url");
    $.getJSON(dataUrl).done(function(jsonData) {
        const filteredData = jsonData.data.filter(item => item.meta >= 2);
        const winRateRange = calculateAxisRange(filteredData, 'win');
        const metaShareRange = calculateAxisRange(filteredData, 'meta');

        const chartData = filteredData.map(item => ({
            name: item.name,
            value: [item.win, item.meta, item.matches],
            matches: item.matches,
            itemStyle: {
                color: getColorByMatches(item.matches)
            }
        }));

        const option = {
          title: {
              text: 'Win rate vs Meta share',
              subtext: "swumeta.net",
              left: 'center',
              textStyle: {
                  color: '#ffffff'
              }
          },
          backgroundColor: '#121212',
          tooltip: {
              trigger: 'item',
              formatter: function(params) {
                  const win = params.value[0];
                  const meta = params.value[1];
                  const matches = params.value[2];
                  const name = params.data.name;
                  return `<strong>${name}</strong><br/>Meta share: ${meta}%<br/>Win rate: ${win}%<br/>Matches: ${matches}`;
              }
          },
          xAxis: {
              type: 'value',
              name: 'Win rate (%)',
              nameLocation: 'middle',
              nameGap: 30,
              min: 0,
              max: Math.max(60, winRateRange.max),
              splitNumber: 10,
              axisLine: {
                  show: true
              },
              splitLine: {
                  show: true,
                  lineStyle: {
                      type: 'dashed',
                      opacity: 0.7
                  }
              }
          },
          yAxis: {
              type: 'value',
              name: 'Meta share (%)',
              nameLocation: 'middle',
              nameGap: 30,
              min: 0,
              max: Math.max(15, metaShareRange.max),
              splitNumber: 10,
              axisLine: {
                  show: true
              },
              splitLine: {
                  show: true,
                  lineStyle: {
                      type: 'dashed',
                      opacity: 0.7
                  }
              }
          },
          grid: {
              left: '5%',
              right: '5%',
              bottom: '10%',
              top: '15%',
              containLabel: true
          },
          series: [{
              type: 'scatter',
              symbolSize: function(data) {
                  return calculateSymbolSize(data[2]);
              },
              data: chartData,
              label: {
                  show: false,
                  formatter: function(params) {
                      return params.data.name;
                  },
                  position: 'top',
                  fontSize: 12
              },
              emphasis: {
                  label: {
                      show: false
                  },
                  itemStyle: {
                      shadowBlur: 10,
                      shadowColor: 'rgba(0, 0, 0, 0.5)'
                  }
              }
          }]
        };

        const chartDom = elem[0];
        const myChart = echarts.init(chartDom);
        myChart.setOption(option);
        window.addEventListener('resize', function() {
          myChart.resize();
        });
    });
}

document.addEventListener('DOMContentLoaded', function() {
    $(".winrates-matrix").each(function() {
        initWinratesMatrix($(this));
    });
    $(".winrates-chart").each(function() {
        initWinratesChart($(this));
    });
});
