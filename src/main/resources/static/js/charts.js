function createHorizontalBarChart(elem, dataUrl, options = {}) {
    // Default options
    const defaultOptions = {
        title: 'Untitled',
        valueLabel: 'Value',
        valueAsPercentage: false,
        dynamicHeight: true
    };

    // Merge default options with provided options
    const chartOptions = {...defaultOptions, ...options};

    // Get container element
    const chartDom = elem[0];

    // Chart options
    const option = {
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            },
        },
        title: {
            text: chartOptions.title,
            subtext: "swumeta.net",
            left: 'center',
            textStyle: {
                color: '#ffffff'
            }
        },
        backgroundColor: '#121212',
        legend: {
            show: false
        },
        grid: {
            left: '5%',
            right: '5%',
            bottom: '5%',
            containLabel: true
        },
        xAxis: {
            type: 'value',
            name: '',
            nameLocation: 'middle',
        },
        yAxis: {
            type: 'category',
            data: [],
            axisLabel: {
                fontSize: 13,
                color: '#e0e0e0'
            }
        },
        series: [
            {
                name: chartOptions.valueLabel,
                type: 'bar',
                data: [],
                itemStyle: {
                    color: '#b03a2e'
                },
                label: {
                    show: true,
                    position: 'right',
                    formatter: '{c}',
                    color: '#fff'
                }
            }
        ]
    };

    // Initialize chart
    const myChart = echarts.init(chartDom);
    myChart.showLoading({
      maskColor: 'rgba(0,0,0,0)',
      textColor: '#fff'
    });
    myChart.setOption(option);

    // Resize handling
    window.addEventListener('resize', function() {
        myChart.resize();
    });

    $.getJSON(dataUrl).done(function(data) {
        // Process data
        let processedData = [...data.data];

        // Calculate total for percentages
        const totalValue = processedData.reduce((sum, item) => sum + item.value, 0);

        // Prepare data for ECharts
        const names = processedData.map(item => item.name);
        const values = processedData.map(item => item.value);
        const newOption = {
            tooltip: {
                formatter: function(params) {
                    if(chartOptions.valueAsPercentage) {
                        const data = params[0];
                        const index = data.dataIndex;
                        return `<strong style="color:#000">${names[index]}</strong><br/>` +
                               `<span style="color:#000">${values[index]}%</span>`;
                    } else {
                        const data = params[0];
                        const index = data.dataIndex;
                        const percentage = (values[index] / totalValue * 100).toFixed(1);
                        return `<strong style="color:#000">${names[index]}</strong><br/>` +
                               `<span style="color:#000">${values[index]} (${percentage}%)</span>`;
                    }
                }
            },
            yAxis: {
                data: names,
            },
            series: [{
                data: values,
            }]
        };
        if(chartOptions.dynamicHeight) {
            let h = (processedData.length * 40) + 10;
            if(processedData.length > 32) {
                h = (processedData.length * 30);
            }
            if(h < 100) {
                h = 140;
            }
            chartDom.style.height = (h + "px");
        }
        myChart.hideLoading();
        myChart.setOption(newOption);
        myChart.resize();
    }).fail(function(jqXHR, textStatus, errorThrown) {
         console.error("Failed to load chart data:", textStatus, errorThrown);
    });

    // Return chart instance for further manipulation if needed
    return myChart;
}

function initBarChart(elem) {
    const dataTitle = elem.attr("data-title");
    const dataPercent = "true" == elem.attr("percent")
    const dataUrl = elem.attr("data-url");
    const options = {
          title: dataTitle,
          valueAsPercentage: dataPercent
    };
    createHorizontalBarChart(elem, dataUrl, options);
}

document.addEventListener('DOMContentLoaded', function() {
    $(".bar-chart").each(function() {
        initBarChart($(this));
    });
});
