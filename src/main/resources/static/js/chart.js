/**
 * Generate a dynamic color palette based on the number of items
 * @param {number} numColors - Number of colors to generate
 * @return {array} Array of color strings in HSL format
 */
function generateColorPalette(numColors) {
    const colors = [];
    for (let i = 0; i < numColors; i++) {
        // Generate HSL colors well distributed across the spectrum
        const hue = (i * 360 / numColors) % 360;
        const saturation = 70 + Math.random() * 20; // between 70% and 90%
        const lightness = 45 + Math.random() * 10;  // between 45% and 55%
        colors.push(`hsl(${hue}, ${saturation}%, ${lightness}%)`);
    }
    return colors;
}

/**
 * Create a horizontal bar chart
 * @param {string} containerId - ID of the HTML element to contain the chart
 * @param {string} dataUrl - Path to a JSON structure
 * @param {object} options - Optional configuration options
 */
function createHorizontalBarChart(containerId, dataUrl, options = {}) {
    // Default options
    const defaultOptions = {
        title: 'Untitled',
        valueLabel: 'Value',
        dynamicHeight: true
    };

    // Merge default options with provided options
    const chartOptions = {...defaultOptions, ...options};

    // Get container element
    const chartDom = document.getElementById(containerId);
    if (!chartDom) {
        console.error(`Container with ID "${containerId}" not found`);
        return;
    }

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
                    const data = params[0];
                    const index = data.dataIndex;
                    const percentage = (values[index] / totalValue * 100).toFixed(1);
                    return `<strong style="color:#000">${names[index]}</strong><br/>` +
                           `<span style="color:#000">${chartOptions.valueLabel}: ${values[index]}</span><br/>` +
                           `<span style="color:#000">Percentage: ${percentage}%</span>`;
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
