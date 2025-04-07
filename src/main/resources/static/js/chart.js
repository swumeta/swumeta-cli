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
 * @param {array} data - Array of objects with name and value properties
 * @param {object} options - Optional configuration options
 */
function createHorizontalBarChart(containerId, data, options = {}) {
    // Default options
    const defaultOptions = {
        title: 'Untitled',
        valueLabel: 'Value',
        sortData: true,
        reverseOrder: true
    };

    // Merge default options with provided options
    const chartOptions = {...defaultOptions, ...options};

    // Get container element
    const chartDom = document.getElementById(containerId);
    if (!chartDom) {
        console.error(`Container with ID "${containerId}" not found`);
        return;
    }

    // Initialize chart with dark theme
    const myChart = echarts.init(chartDom);

    // Process data
    let processedData = [...data];

    // Sort data if specified
    if (chartOptions.sortData) {
        processedData = processedData.sort((a, b) => {
            // Always put "Others" at the end regardless of value
            if (a.name === "Others") return 1;
            if (b.name === "Others") return -1;
            // Normal sorting by value for other items
            return b.value - a.value;
        });
    }

    // Calculate total for percentages
    const totalValue = processedData.reduce((sum, item) => sum + item.value, 0);

    // Reverse data if specified
    if (chartOptions.reverseOrder) {
        processedData = processedData.reverse();
    }

    // Prepare data for ECharts
    const names = processedData.map(item => item.name);
    const values = processedData.map(item => item.value);

    // Generate color palette
    const colorPalette = generateColorPalette(data.length);

    // Chart options
    const option = {
        tooltip: {
            trigger: 'axis',
            axisPointer: {
                type: 'shadow'
            },
            formatter: function(params) {
                const data = params[0];
                const index = data.dataIndex;
                const percentage = (values[index] / totalValue * 100).toFixed(1);
                return `<strong style="color:#000">${names[index]}</strong><br/>` +
                       `<span style="color:#000">${chartOptions.valueLabel}: ${values[index]}</span><br/>` +
                       `<span style="color:#000">Percentage: ${percentage}%</span>`;
            }
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
            data: names,
            axisLabel: {
                fontSize: 13,
                color: '#e0e0e0'
            }
        },
        series: [
            {
                name: chartOptions.valueLabel,
                type: 'bar',
                data: values,
                itemStyle: {
                    color: function(params) {
                        if (names[params.dataIndex] === "Others") {
                            return "#555";
                        }
                        return colorPalette[params.dataIndex];
                    }
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

    // Apply options to the chart
    myChart.setOption(option);

    // Resize handling
    window.addEventListener('resize', function() {
        myChart.resize();
    });

    // Return chart instance for further manipulation if needed
    return myChart;
}
