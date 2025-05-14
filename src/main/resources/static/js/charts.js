const BASE_COLOR = '#b03a2e';

function hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
    } : null;
}

function rgbToHex(r, g, b) {
    return "#" + ((1 << 24) + (r << 16) + (g << 8) + b).toString(16).slice(1);
}

function generateColorPalette(baseColor, count) {
    const colors = [];
    const rgb = hexToRgb(baseColor);

    if (!rgb) return [];

    if (count === 1) {
        colors.push(baseColor);
    } else if (count === 2) {
        colors.push(baseColor);

        // Create a complementary color
        const complement = {
            r: Math.min(255, rgb.r + 120),
            g: Math.min(255, rgb.g + 80),
            b: Math.min(255, rgb.b + 40)
        };
        colors.push(rgbToHex(complement.r, complement.g, complement.b));
    } else {
        // For more colors: create variations using HSL-like approach
        for (let i = 0; i < count; i++) {
            const factor = i / count;

            // Create variations by adjusting brightness and slightly shifting hue
            const brightness = 0.7 + (factor * 0.6);
            const hueShift = (i % 2 === 0 ? 1 : -1) * (factor * 30);

            let newR = Math.round(rgb.r * brightness + hueShift);
            let newG = Math.round(rgb.g * brightness);
            let newB = Math.round(rgb.b * brightness - hueShift);

            // Ensure values are within valid range
            newR = Math.max(0, Math.min(255, newR));
            newG = Math.max(0, Math.min(255, newG));
            newB = Math.max(0, Math.min(255, newB));

            colors.push(rgbToHex(newR, newG, newB));
        }
    }

    return colors;
}

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
                    color: BASE_COLOR
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
            let h = (processedData.length * 45) + 10;
            if(processedData.length > 32) {
                h = (processedData.length * 40);
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
    const dataPercent = elem.attr("data-percent") == "true";
    const dataUrl = elem.attr("data-url");
    const options = {
          title: dataTitle,
          valueAsPercentage: dataPercent
    };
    createHorizontalBarChart(elem, dataUrl, options);
}

function createPieChart(elem, dataUrl, options = {}) {
    // Default options
    const defaultOptions = {
        title: 'Untitled',
        valueLabel: 'Value',
    };

    // Merge default options with provided options
    const chartOptions = {...defaultOptions, ...options};

    // Get container element
    const chartDom = elem[0];

    // Chart options
    const option = {
        title: {
            text: chartOptions.title,
            subtext: "swumeta.net",
            left: 'center',
            textStyle: {
                color: '#ffffff'
            }
        },
        tooltip: {
            show: false
        },
        backgroundColor: '#121212',
        legend: {
            show: false
        },
        series: [
            {
                name: chartOptions.valueLabel,
                type: 'pie',
                radius: ['35%', '60%'],
                center: ['50%', '50%'],
                top: 40,
                avoidLabelOverlap: false,
                itemStyle: {
                    borderRadius: 6,
                    borderColor: '#fff',
                    borderWidth: 1
                },
                label: {
                    show: false,
                    position: 'center'
                },
                emphasis: {
                    label: {
                        show: true,
                        fontSize: 16,
                        formatter: function(params) {
                            return '{name|' + params.name + '}\n{percent|' + Math.round(params.percent) + '%}';
                        },
                        rich: {
                            name: {
                                fontSize: 16,
                                fontWeight: 'bold',
                                color: '#fff',
                                lineHeight: 20
                            },
                            percent: {
                                fontSize: 14,
                                color: '#ccc',
                                lineHeight: 10
                            }
                        }
                    },
                    itemStyle: {
                        shadowBlur: 20,
                        shadowOffsetX: 0,
                        shadowColor: 'rgba(0, 0, 0, 0.3)'
                    }
                },
                labelLine: {
                    show: false
                },
                animationType: 'scale',
                animationEasing: 'elasticOut',
                animationDelay: function(idx) {
                    return Math.random() * 200;
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

        const colors = generateColorPalette(BASE_COLOR, processedData.length);

        // Prepare data for ECharts
        const chartData = processedData.map((item, index) => ({
            value: item.value,
            name: item.name,
            itemStyle: {
                color: colors[index]
            }
        }));
        const newOption = {
            series: [{
                data: chartData,
            }]
        };
        chartDom.style.height = "100%";
        chartDom.style.minHeight = "230px";
        myChart.hideLoading();
        myChart.setOption(newOption);
        myChart.resize();
    }).fail(function(jqXHR, textStatus, errorThrown) {
         console.error("Failed to load chart data:", textStatus, errorThrown);
    });

    // Return chart instance for further manipulation if needed
    return myChart;
}

function initPieChart(elem) {
    const dataTitle = elem.attr("data-title");
    const dataUrl = elem.attr("data-url");
    const options = {
          title: dataTitle,
    };
    createPieChart(elem, dataUrl, options);
}

document.addEventListener('DOMContentLoaded', function() {
    $(".pie-chart").each(function() {
        initPieChart($(this));
    });
});

document.addEventListener('DOMContentLoaded', function() {
    $(".bar-chart").each(function() {
        initBarChart($(this));
    });
});
